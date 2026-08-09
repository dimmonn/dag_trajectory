package com.uj.graph.dag_trajectory.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GithubProjectController {

    @GetMapping("/github-project")
    public String hello(@RequestParam String owner, @RequestParam String name) {
        return owner + " " + name;
    }
}
