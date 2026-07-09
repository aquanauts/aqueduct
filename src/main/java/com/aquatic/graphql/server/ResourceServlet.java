package com.aquatic.graphql.server;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.Serial;

class ResourceServlet extends HttpServlet {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String resourceRoot;

    ResourceServlet(String resourceRoot) {
        this.resourceRoot = resourceRoot;
    }

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try (var stream = ResourceServlet.class.getResourceAsStream(resourceRoot + "/index.html")) {
            if (stream == null) {
                resp.setStatus(404);
                resp.getWriter().println("<h1>404 Not Found</h1>");
            } else {
                resp.setContentType("text/html");
                resp.getWriter().write(new String(stream.readAllBytes()));
            }
        }
    }
}
