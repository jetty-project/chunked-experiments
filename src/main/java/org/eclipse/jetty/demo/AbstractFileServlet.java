package org.eclipse.jetty.demo;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@SuppressWarnings("serial")
public abstract class AbstractFileServlet extends HttpServlet
{
    private ServletContext context;
    
    @Override
    public void init(ServletConfig config) throws ServletException
    {
        this.context = config.getServletContext();
        super.init(config);
    }
    
    /**
     * Copy the entire {@link InputStream} to the {@link OutputStream}
     *
     * @param in
     *            the input stream to read from
     * @param out
     *            the output stream to write to
     * @throws IOException
     */
    public static void copy(InputStream in, OutputStream out) throws IOException
    {
        byte buffer[] = new byte[BUFFER_SIZE];
        int len = BUFFER_SIZE;

        while (true)
        {
            len = in.read(buffer,0,BUFFER_SIZE);
            if (len < 0)
            {
                break;
            }
            out.write(buffer,0,len);
        }
    }

    private static final int BUFFER_SIZE = 8096;

    public void sendFile(HttpServletResponse response, File file) throws IOException
    {
        ServletOutputStream out = response.getOutputStream();
        try (InputStream in = new FileInputStream(file))
        {
            copy(in,out);
        }
    }

    public void setContentType(HttpServletResponse response, File file)
    {
        if (file.getName().endsWith("txt"))
        {
            response.setContentType("text/plain");
        }
        else if (file.getName().endsWith("mp4"))
        {
            response.setContentType("video/mpeg");
        }
        else
        {
            response.setContentType("application/octet-stream");
        }
    }

    public File getFileRef(HttpServletRequest request, HttpServletResponse response) throws UnsupportedEncodingException, IOException
    {
        String fileName = URLDecoder.decode(request.getPathInfo(),"UTF-8");

        File file = new File(AppInit.getBaseDir(context),fileName);

        if (!file.exists() || !file.isFile())
        {
            context.log("Unable to find file: " + fileName);
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        return file;
    }
}
