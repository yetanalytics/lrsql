# Yet Analytics Open Source Contribution Guidelines

## Welcome to the Yet Analytics Open Source Community!

Thank you for your interest in contributing to Yet Analytics Open Source projects. It is our goal in maintaining these Open Source projects to provide useful tools for the entire xAPI Community. We welcome feedback and contributions from users, stakeholders and engineers alike.

The following document outlines the policies, methodology, and guidelines for contributing to our open source projects.

## Code of Conduct

The Yet Analytics Open Source Community has a [Code of Conduct](CODE_OF_CONDUCT.md) which should be read and followed when contributing in any way.

## Issue Reporting

Yet Analytics encourages users to contribute by reporting any issues or enhancement suggestions via [GitHub Issues](https://github.com/yetanalytics/lrsql/issues).

Before submission, we encourage you to read through the existing [Documentation](docs/index.md) to ensure that the issue has not been addressed or explained.

### Issue Templates

If the repository has an Issue Template, please follow the template as much as possible in your submission as this helps our team more quickly triage and understand the issues you are seeing or enhancements you are suggesting.

### Security Issues

If you believe you have found a potential security issue in the codebase of a Yet Analytics project, please do NOT open an issue. Email [team@yetanalytics.com](mailto:team@yetanalytics.com) directly instead.

## Code Contributions

### Methodology

For community contribution to the codebase of a Yet Analytics project we ask that you follow the [Fork and Pull](https://docs.github.com/en/github/collaborating-with-pull-requests/proposing-changes-to-your-work-with-pull-requests/creating-a-pull-request-from-a-fork) methodology for proposing changes. In short, this method requires you to do the following:

- Fork the repository
- Clone your Fork and perform the appropriate changes on a branch
- Push the changes back to your Fork
- Make sure your Fork is up to date with the latest `main` branch in the central repository (merge upstream changes)
- Submit a Pull Request using your fork's branch

The Yet Analytics team will then review the changes, and may make suggestions on GitHub before a final decision is made. The Yet team reviews Pull Requests regularly and we will be notified of its creation and all updates immediately.

### Style

For contributions in Clojure, we would suggest you read this [Clojure Style Guide](https://github.com/bbatsov/clojure-style-guide) as it is one that we generally follow in our codebases.

### Tests

In order for us to merge a Pull Request it must pass the `make ci` Makefile target. This target runs a set of unit, integration and/or conformance tests which verify the build's behavior. Please run this target and remediate any issues before submitting a Pull Request.

We ask that when adding or changing functionality in the system that you examine whether it is a candidate for additional or modified test coverage and add it if so. You can see what sort of tests are in place currently by exploring the namespaces in `src/test`.

## License and Copyright

By contributing to a Yet Analytics Open Source project you agree that your contributions will be licensed under its [Apache License 2.0](LICENSE).
