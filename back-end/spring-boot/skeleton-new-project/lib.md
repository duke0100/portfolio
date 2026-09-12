# Table of Contents:
- [1. Directory structure](#1-directory-structure)
- [2. Pom](#2-pom)

## 1. Directory structure
This library does not include a `MainApplication` class or a `resources` folder

## 2. Pom
The library’s pom.xml is mostly similar to a standard application POM. However, it includes an additional `distributionManagement` section to specify where the built artifact will be published.

By default, Maven stores built artifacts in the local `.m2` repository.