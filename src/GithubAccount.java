import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.OptionalLong;

public class GithubAccount {
    private final String baseApiUrl = "https://api.github.com";
    private final String userAllReposEndpoint = "/user/repos";
    private final String repoEndpoint = "/repos";
    private String token;
    private int secondaryRateLimit = 0;

    public GithubAccount(String token) {
        this.token = token;
    }

    private long getRateLimitDuration(HttpResponse<String> response) {
        long retryAfterSeconds;

        JSONObject responseObject = new JSONObject(response.body());
        if (responseObject.has("retry-after")) {
            retryAfterSeconds = responseObject.getLong("retry-after");
            secondaryRateLimit = 0;
        } else {
            // Fallback
            if (secondaryRateLimit == 0) {
                secondaryRateLimit = 60;
            } else {
                secondaryRateLimit *= 2;
            }
            retryAfterSeconds = secondaryRateLimit;
        }

        return retryAfterSeconds;
    }

    private HttpResponse<String> post(String endpoint, String body) throws InterruptedException, IOException {
        while (true) {
            HttpClient client = HttpClient.newHttpClient();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseApiUrl + endpoint))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "token " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 429) {
                secondaryRateLimit = 0;
                return response;
            }

            // Rate limited
            long retryAfterSeconds = getRateLimitDuration(response);
            System.out.println("Rate limited for " + retryAfterSeconds + " seconds.");
            Thread.sleep(retryAfterSeconds * 1000);
            System.out.println("Retrying...");
        }
    }

    private HttpResponse<String> get(String endpoint) throws InterruptedException, IOException {
        while (true) {
            HttpClient client = HttpClient.newHttpClient();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseApiUrl + endpoint))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "token " + token)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 429) {
                secondaryRateLimit = 0;
                return response;
            }

            // Rate limited
            long retryAfterSeconds = getRateLimitDuration(response);
            System.out.println("Rate limited for " + retryAfterSeconds + " seconds.");
            Thread.sleep(retryAfterSeconds * 1000);
            System.out.println("Retrying...");
        }
    }

    private HttpResponse<String> put(String endpoint, String body) throws IOException, InterruptedException {
        while (true) {
            HttpClient client = HttpClient.newHttpClient();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseApiUrl + endpoint))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "token " + token)
                    .PUT(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 429) {
                secondaryRateLimit = 0;
                return response;
            }

            // Rate limited
            long retryAfterSeconds = getRateLimitDuration(response);
            System.out.println("Rate limited for " + retryAfterSeconds + " seconds.");
            Thread.sleep(retryAfterSeconds * 1000);
            System.out.println("Retrying...");
        }
    }

    public List<String> getRepos() throws IOException, InterruptedException {
        ArrayList<String> repos = new ArrayList<>();
        final int perPage = 10;
        int pageNumber = 1;

        while (true) {
            HttpResponse<String> response = get(userAllReposEndpoint + "?per_page=" + perPage + "&page=" + pageNumber);

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JSONArray reposArray = new JSONArray(response.body());
                for (int i = 0; i < reposArray.length(); i++) {
                    JSONObject repo = reposArray.getJSONObject(i);
                    repos.add(repo.getString("full_name"));
                }

                if (reposArray.length() < perPage) {
                    break;
                }

                pageNumber++;
            } else {
                throw new RuntimeException("Could not get repos list, status code " + response.statusCode());
            }
        }

        return repos;
    }

    public String createPullRequest(String repoFullName, String branchName, String fileName, String fileContents, String prTitle, String prBody) throws IOException, InterruptedException {
        // First, get default branch name
        HttpResponse<String> response = get(repoEndpoint + "/" + repoFullName);
        String default_branch_name;
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            JSONObject repoObject = new JSONObject(response.body());
            default_branch_name = repoObject.getString("default_branch");
        } else {
            throw new RuntimeException("Could not get repo information, status code " + response.statusCode());
        }

        // Get branch latest commit SHA
        response = get(repoEndpoint + "/" + repoFullName + "/branches/" + default_branch_name);
        String lastCommitSha;
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            JSONObject branchObject = new JSONObject(response.body());
            lastCommitSha = branchObject.getJSONObject("commit").getString("sha");
        } else {
            throw new RuntimeException("Could not get branch information for " + default_branch_name + ", status code " + response.statusCode());
        }

        // Create new branch
        String postBody = String.format("""
            {"ref": "refs/heads/%s",
            "sha": "%s"}""", branchName, lastCommitSha);

        response = post(repoEndpoint + "/" + repoFullName + "/git/refs", postBody);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Could not create new branch, status code " + response.statusCode());
        }

        // Add file
        String putBody = String.format("""
            {"message": "Add new file",
            "content": "%s",
            "branch": "%s"}""", Base64.getEncoder().encodeToString(fileContents.getBytes()), branchName);

        response = put(repoEndpoint + "/" + repoFullName + "/contents/" + fileName, putBody);

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Could not add file to branch, status code " + response.statusCode());
        }

        // Create pull request
        postBody = String.format("""
            {"title": "%s",
            "head": "%s",
            "base": "%s",
            "body": "%s"}""", prTitle, branchName, default_branch_name, prBody);

        response = post(repoEndpoint + "/" + repoFullName + "/pulls", postBody);

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Could not create PR, status code " + response.statusCode());
        }

        JSONObject responseObject = new JSONObject(response.body());

        return responseObject.getString("html_url");
    }
}