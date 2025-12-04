# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

### Added

- Support for nested manifests, e.g. index containing another index (aka list image)

### Changed

- Revert chunked image upload changes due to issues in production

## [v1.3.3] - 2025-10-17

### Fixed

- Fix chunked image upload to be faster