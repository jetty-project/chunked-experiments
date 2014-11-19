package org.eclipse.jetty.demo;

import java.io.File;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

public class AppInit implements ServletContextListener
{
    private static final String BASEDIR_KEY = AppInit.class.getName() + ".basedir";
    
    public static File getBaseDir(ServletContext ctx)
    {
        return (File)ctx.getAttribute(BASEDIR_KEY);
    }

    @Override
    public void contextInitialized(ServletContextEvent sce)
    {
        ServletContext ctx = sce.getServletContext();

        String ref = "/quotes.txt";

        String basePath = ctx.getRealPath(ref);
        ctx.log("Real Path (" + ref + ") => " + basePath);
        if (basePath == null)
        {
            throw new RuntimeException("Unable to find quotes.txt");
        }
        int idx = basePath.length() - ref.length();
        File basedir = new File(basePath.substring(0,idx));
        ctx.log("Base Dir => " + basedir.getAbsolutePath());
        ctx.setAttribute(BASEDIR_KEY,basedir);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce)
    {
    }
}
