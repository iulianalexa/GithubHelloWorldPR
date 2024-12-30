# Github Hello World PR

## Overview

This CLI application prints a list of all your repositories, allowing you to
select one. After selecting a repository, a pull request adding a file containing a
"Hello world" message will be created.

This application was created as a solution to the task proposed by the 2025 JetBrains Internship Project **Merge Request Assistant** required for application.

## Dependencies

In order to run this Java application, the `org.json` package is required.

## Configuration

A JSON configuration file is required to pass the personal access token for your Github account.

This configuration file is named `config.json`, and it has the following format:

```json
{
  "token": "your personal access token here"
}
```