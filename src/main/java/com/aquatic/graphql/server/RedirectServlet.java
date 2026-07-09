package com.aquatic.graphql.server;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.Serial;

class RedirectServlet extends HttpServlet {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String target;

    RedirectServlet(String target) {
        this.target = target;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.sendRedirect(target);
    }
}
