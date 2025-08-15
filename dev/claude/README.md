# Claude Code Configuration

This directory contains configuration files specific to Claude Code.

## CLAUDE.md Symlink Pattern

To keep the project root directory clean while supporting multiple AI
coding tools, the `CLAUDE.md` file can be symlinked from this directory
to the project root.

### Setup

Create a symlink from the project root to this directory's CLAUDE.md:

```bash
# From the project root directory
ln -s dev/claude/CLAUDE.md CLAUDE.md
```

### Benefits

- **Clean project root**: Keeps AI tool-specific configuration files
  organized in subdirectories

- **Multi-tool support**: Different AI coding tools can have their own
  configuration directories (e.g., `dev/cursor/`, `dev/copilot/`, etc.)

- **Version control**: The actual configuration file is tracked in
  version control while the symlink provides the expected location

- **Team consistency**: All team members can use the same configuration
  regardless of their preferred AI coding tool

### Directory Structure

```
dev/
└── claude/
    ├── README.md        # This file
    └── CLAUDE.md        # Claude Code project instructions
```
