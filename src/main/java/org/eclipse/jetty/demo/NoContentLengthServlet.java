package org.eclipse.jetty.demo;

import java.io.File;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@SuppressWarnings("serial")
public class NoContentLengthServlet extends AbstractFileServlet
{
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        File file = getFileRef(request,response);
        if (file == null)
        {
            return;
        }

        setContentType(response,file);
        // response.setContentLength((int)file.length());
        sendFile(response,file);
    }
}
