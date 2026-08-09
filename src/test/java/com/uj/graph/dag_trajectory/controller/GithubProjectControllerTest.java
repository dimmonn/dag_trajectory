package com.uj.graph.dag_trajectory.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GithubProjectControllerTest {

    @Test
    void returnsHello() {
        var controller = new GithubProjectController();

        assertEquals("hello", controller.hello("octocat", "dag"));
    }
}
