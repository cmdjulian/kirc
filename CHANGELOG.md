# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

## [v1.4.0] - 2025-12-17

### Added

- Switching between Streamed and Chunked upload modes for image upload

### Changed

- Replaced Fuel http library with Ktor http client
- Error handling improvements
- Improved logging
- Improved basic / bearer authentication handling

### Fixed

- Problems related to fuel library

## [v1.3.4] - 2025-12-10

### Added

- Support for nested manifests, e.g. index containing another index (aka list image)

### Fixed

- Fix chunked image upload

## [v1.3.3] - 2025-10-17

### Fixed

- Fix chunked image upload to be faster

## [v1.3.2] - 2025-10-01

### Fixed

- Update build integration

## [v1.3.1] - 2025-10-01

### Changed

- Allow simultaneous upload of max. 3 blobs

### Fix

- Fix image extractor

## [v1.3.0] - 2025-08-21

### Added

- Image Upload feature
- Image download feature