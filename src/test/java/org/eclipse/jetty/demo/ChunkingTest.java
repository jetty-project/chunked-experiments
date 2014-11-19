package org.eclipse.jetty.demo;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.Charset;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.toolchain.test.IO;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.FragmentConfiguration;
import org.eclipse.jetty.webapp.JettyWebXmlConfiguration;
import org.eclipse.jetty.webapp.MetaInfConfiguration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebInfConfiguration;
import org.eclipse.jetty.webapp.WebXmlConfiguration;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

public class ChunkingTest
{
    private static Server server;
    private static URI serverURI;

    @BeforeClass
    public static void startServer() throws Exception
    {
        server = new Server(9090);

        HandlerCollection handlers = new HandlerCollection();
        WebAppContext webapp = new WebAppContext();
        webapp.setContextPath("/");
        webapp.setWar("src/main/webapp");
        webapp.setExtraClasspath("target/classes");
        webapp.setConfigurations(new Configuration[] { new WebInfConfiguration(), new WebXmlConfiguration(), new MetaInfConfiguration(),
                new FragmentConfiguration(), new JettyWebXmlConfiguration() });

        handlers.addHandler(webapp);
        handlers.addHandler(new DefaultHandler());

        server.setHandler(handlers);
        server.start();
        serverURI = new URI("http://localhost:9090/");
    }

    @AfterClass
    public static void stopServer() throws Exception
    {
        server.stop();
    }

    private void assertIsChunked(StringBuilder request, URI uri) throws UnknownHostException, IOException
    {
        assertThat("Chunking",identifyResponseChunking(request,uri),is(true));
    }

    private void assertIsNotChunked(StringBuilder request, URI uri) throws UnknownHostException, IOException
    {
        assertThat("Chunking",identifyResponseChunking(request,uri),is(false));
    }

    private boolean identifyResponseChunking(StringBuilder request, URI uri) throws UnknownHostException, IOException
    {
        try (Socket sock = new Socket(uri.getHost(),uri.getPort()); OutputStream out = sock.getOutputStream(); InputStream in = sock.getInputStream())
        {
            sock.setSoTimeout(500);
            writeRequest(request,out);
            String resp = readResponse(in);
            StringBuilder respHeader = new StringBuilder();
            String contentSample = null;
            try (StringReader reader = new StringReader(resp); BufferedReader buf = new BufferedReader(reader))
            {
                String line;
                while ((line = buf.readLine()) != null)
                {
                    line = line.trim();
                    if (line.length() == 0)
                    {
                        // end of header.
                        break;
                    }
                    respHeader.append(line).append("\r\n");
                }
                contentSample = buf.readLine();
            }

            String firstLine = "";
            int idx = resp.indexOf('\r');
            if (idx > 0)
            {
                firstLine = resp.substring(0,idx);
            }

            System.out.println("--Request--");
            System.out.print(request);
            System.out.println("--Response--");
            System.out.println(respHeader);

            // Check the first line of content (instead of looking for the Transfer-Encoding header)
            boolean chunking = false;
            String sample = "<null>";
            if (contentSample != null)
            {
                chunking = contentSample.matches("^[0-9]*$");
                int sampleLen = contentSample.length();
                sample = String.format("[%s]",contentSample.substring(0,Math.min(15,sampleLen)));
            }
            if (chunking)
            {
                System.out.printf("--[CHUNKED] - sample: %s%n",sample);
            }
            else
            {
                System.out.printf("--[NOT-CHUNKED] - sample: %s%n",sample);
            }
            assertThat("Response",firstLine,containsString("HTTP/1.1 200 OK"));
            return chunking;
        }
    }

    private String readResponse(InputStream in) throws IOException
    {
        StringWriter writer = new StringWriter();
        InputStreamReader reader = new InputStreamReader(in,Charset.forName("UTF-8"));
        IO.copy(reader,writer);
        writer.flush();
        return writer.toString();
    }

    private void writeRequest(StringBuilder req, OutputStream out) throws IOException
    {
        StringReader strReader = new StringReader(req.toString());
        OutputStreamWriter writer = new OutputStreamWriter(out,Charset.forName("UTF-8"));
        IO.copy(strReader,writer);
        writer.flush();
    }

    @Test
    public void testHttp10_KeepAlive_WithContentLength() throws UnknownHostException, IOException
    {
        StringBuilder request = new StringBuilder();
        request.append("GET /withlen/twain.txt HTTP/1.0\r\n");
        request.append("Host: localhost:9090\r\n");
        request.append("Connection: keep-alive\r\n");
        request.append("\r\n");

        // second request (just to get the connection to close)
        request.append("GET / HTTP/1.0\r\n");
        request.append("Host: localhost:9090\r\n");
        request.append("\r\n");

        assertIsNotChunked(request,serverURI.resolve("/withlen/twain.txt"));
    }

    @Test
    public void testHttp10_NoKeepAlive_WithContentLength() throws UnknownHostException, IOException
    {
        StringBuilder request = new StringBuilder();
        request.append("GET /withlen/twain.txt HTTP/1.0\r\n");
        request.append("Host: localhost:9090\r\n");
        request.append("\r\n");

        assertIsNotChunked(request,serverURI.resolve("/withlen/twain.txt"));
    }

    @Test
    public void testHttp11_Close_WithContentLength() throws UnknownHostException, IOException
    {
        StringBuilder request = new StringBuilder();
        request.append("GET /withlen/twain.txt HTTP/1.1\r\n");
        request.append("Host: localhost:9090\r\n");
        request.append("Connection: close\r\n");
        request.append("\r\n");

        assertIsNotChunked(request,serverURI.resolve("/withlen/twain.txt"));
    }

    @Test
    public void testHttp11_Close_Identity() throws UnknownHostException, IOException
    {
        StringBuilder request = new StringBuilder();
        request.append("GET /identity/twain.txt HTTP/1.1\r\n");
        request.append("Host: localhost:9090\r\n");
        request.append("Connection: close\r\n");
        request.append("\r\n");

        assertIsNotChunked(request,serverURI.resolve("/identity/twain.txt"));
    }

    @Test
    public void testHttp10_KeepAlive_NoContentLength() throws UnknownHostException, IOException
    {
        StringBuilder request = new StringBuilder();
        request.append("GET /nolen/twain.txt HTTP/1.0\r\n");
        request.append("Host: localhost:9090\r\n");
        request.append("Connection: keep-alive\r\n");
        request.append("\r\n");

        // second request (just to get the connection to close)
        request.append("GET / HTTP/1.0\r\n");
        request.append("Host: localhost:9090\r\n");
        request.append("\r\n");

        assertIsNotChunked(request,serverURI.resolve("/nolen/twain.txt"));
    }

    @Test
    public void testHttp10_NoKeepAlive_NoContentLength() throws UnknownHostException, IOException
    {
        StringBuilder request = new StringBuilder();
        request.append("GET /nolen/twain.txt HTTP/1.0\r\n");
        request.append("Host: localhost:9090\r\n");
        request.append("\r\n");

        assertIsNotChunked(request,serverURI.resolve("/nolen/twain.txt"));
    }

    @Test
    public void testHttp11_Close_NoContentLength() throws UnknownHostException, IOException
    {
        StringBuilder request = new StringBuilder();
        request.append("GET /nolen/twain.txt HTTP/1.1\r\n");
        request.append("Host: localhost:9090\r\n");
        request.append("Connection: close\r\n");
        request.append("\r\n");

        assertIsNotChunked(request,serverURI.resolve("/nolen/twain.txt"));
    }

    @Test
    public void testHttp11_NoClose_NoContentLength() throws UnknownHostException, IOException
    {
        StringBuilder request = new StringBuilder();
        request.append("GET /nolen/twain.txt HTTP/1.1\r\n");
        request.append("Host: localhost:9090\r\n");
        request.append("\r\n");

        // second request (just to get the connection to close)
        request.append("GET / HTTP/1.1\r\n");
        request.append("Host: localhost:9090\r\n");
        request.append("Connection: close\r\n");
        request.append("\r\n");

        assertIsChunked(request,serverURI.resolve("/nolen/twain.txt"));
    }

    @Test
    @Ignore("This is an invalid scenario for HTTP/1.1, and will alway fail")
    public void testHttp11_NoClose_Identity() throws UnknownHostException, IOException
    {
        StringBuilder request = new StringBuilder();
        request.append("GET /identity/twain.txt HTTP/1.1\r\n");
        request.append("Host: localhost:9090\r\n");
        request.append("\r\n");

        // second request - ideally to get the connection to close.
        // however, this HTTP scenario is invalid.
        // the first request will request a resource that sets Transfer-Encoding: identity
        // however, that requires a Connection: close from the client request as well, which the request does not set.
        request.append("GET / HTTP/1.1\r\n");
        request.append("Host: localhost:9090\r\n");
        request.append("Connection: close\r\n");
        request.append("\r\n");

        assertIsChunked(request,serverURI.resolve("/identity/twain.txt"));
    }
}
