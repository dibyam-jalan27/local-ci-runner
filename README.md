# Local CI Runner

A minimal, lightning-fast CLI tool built in Java that reads YAML pipeline configurations and executes CI/CD steps locally. It provides a GitHub Actions-like experience entirely on your local machine, utilizing Docker for isolated execution environments.

## Features

- **Docker Integration:** Runs your pipelines inside isolated Docker containers (e.g., `alpine`, `ubuntu`).
- **Sequential & Parallel Execution:** Define multiple stages and run steps either sequentially or in parallel.
- **Pipeline Hooks:** Supports `before_pipeline`, `after_pipeline`, `before_step`, and `after_step` hooks for setup and teardown tasks.
- **Environment Variables:** Inject custom environment variables globally or per-step.
- **Resilience:** Built-in support for step `timeout` and `continueOnError`.
- **Notifications:** Send webhook or email notifications upon pipeline completion.
- **Interactive TUI:** Features a Terminal User Interface for real-time logs and run history.
- **Pipeline Dependencies:** Support for matrix builds and child pipelines.

## Requirements

- **Java 17** or higher
- **Maven** 3.8+
- **Docker** (must be running on your host machine)

## Build Instructions

Clone the repository and build the fat JAR using Maven:

```bash
git clone https://github.com/dibyam-jalan27/local-ci-runner.git
cd local-ci-runner
mvn clean package
```

This will produce an executable JAR file at `target/local-ci-runner-1.0.jar`.

## Usage

Run the Local CI Runner by pointing it to a valid YAML pipeline configuration file:

```bash
java -jar target/local-ci-runner-1.0.jar pipelines/full-features-pipeline.yml
```

### Example Pipeline Config (`full-features-pipeline.yml`)

```yaml
pipeline: "Full Features Showcase"
image: "alpine:3.19"

env:
  APP_ENV: "ci"
  BUILD_NUMBER: "100"

hooks:
  before_pipeline:
    - "echo '🚀 Pipeline starting...'"
  after_pipeline:
    - "echo '🏁 Pipeline finished!'"

stages:
  - name: "build"
    steps:
      - name: "Install Dependencies"
        run: "echo 'Installing deps...'"
        timeout: 60

      - name: "Compile"
        run: "echo 'Compiling...'"

  - name: "test"
    steps:
      - name: "Parallel Test Suite"
        parallel:
          - name: "Unit Tests"
            run: "echo 'Unit tests passed!'"
          - name: "Integration Tests"
            run: "echo 'Integration tests passed!'"

  - name: "deploy"
    steps:
      - name: "Deploy"
        run: "echo 'Deployed! BUILD_NUMBER=$BUILD_NUMBER'"

notify:
  on: ["always"]
  webhook:
    url: "https://httpbin.org/post"
```

## Creating Pipelines

You can define complex CI workflows in the `pipelines/` directory. The runner parses the YAML file, launches the specified Docker container (e.g., `image: "alpine:3.19"`), binds necessary volumes, and executes the `run` commands. Matrix expansions, conditional triggers, and rich reporting are all supported out-of-the-box!

## License

MIT License
