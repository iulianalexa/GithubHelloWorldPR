import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;

import org.json.JSONObject;

public class Main {
    private static String getToken(String configFilePath) {
        try {
            String content = new String(Files.readAllBytes(Paths.get(configFilePath)));
            JSONObject jsonObject = new JSONObject(content);

            if (!jsonObject.has("token")) {
                throw new RuntimeException("Token not found in config file.");
            }

            Object tokenValue = jsonObject.get("token");
            if (!(tokenValue instanceof String)) {
                throw new RuntimeException("Token is not string.");
            }

            return (String) tokenValue;
        } catch (Exception e) {
            throw new RuntimeException("Error reading or parsing the configuration file: " + configFilePath + ".");
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        GithubAccount account = new GithubAccount(getToken("config.json"));
        List<String> repos = account.getRepos();

        System.out.println("Please select a repository:");
        for (int i = 0; i < repos.size(); i++) {
            System.out.printf("%d. %s\n", i + 1, repos.get(i));
        }

        Scanner scanner = new Scanner(System.in);

        System.out.print("Enter a number: ");
        String input = scanner.nextLine();
        int inputNumber = 0;

        try {
            inputNumber = Integer.parseInt(input);
        } catch (NumberFormatException _e) {
            System.out.println("You must choose a valid number corresponding to a repository!");
            System.exit(1);
        }

        if (inputNumber < 1 || inputNumber > repos.size()) {
            System.out.println("You must choose a valid number corresponding to a repository!");
            System.exit(1);
        }

        int repoId = inputNumber - 1;

        System.out.println("You have selected: " + repos.get(repoId));
        System.out.print("Is this correct? <y/n> ");

        input = scanner.nextLine();

        if (!input.equals("y")) {
            System.out.println("Aborting...");
            System.exit(1);
        }

        String prUrl = account.createPullRequest(repos.get(repoId), "hello_world", "Hello.txt", "Hello world", "Add new file", "Add hello world file.");

        System.out.println("Pull request created successfully!");
        System.out.println("You can view it here: " + prUrl);
    }
}