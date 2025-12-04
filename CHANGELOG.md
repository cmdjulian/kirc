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