package com.jorge.prreviewer.github;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Component
public class GitHubClient {

    private final WebClient webClient;
    private final String token;

    public GitHubClient(WebClient githubWebClient,
                        @Value("${github.token}") String token) {
        this.webClient = githubWebClient;
        this.token = token;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getPullRequestFiles(String owner, String repo, int prNumber) {
        return webClient.get()
                .uri("/repos/{owner}/{repo}/pulls/{prNumber}/files", owner, repo, prNumber)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .retrieve()
                .bodyToMono(List.class)
                .block();
    }
}