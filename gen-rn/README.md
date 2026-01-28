# gen-rn: XMTP SDK release notes generator

AI-powered release notes generation for XMTP SDK releases using git history analysis and Claude AI.

> [!IMPORTANT]
> Generated release notes must be reviewed by humans before publication. The AI provides a starting point, not a final product.

## Purpose

1. **Engineering sanity check**: Help engineers understand exactly what's included in a release
2. **Technical writing support**: Help technical writers with structured, well-organized release content  
3. **Cross-SDK consistency**: Run in each XMTP SDK repo and compare structured outputs to gauge consistency of feature and fix implementations across SDKs

## Prerequisites

- Node.js 18 or later (required for native fetch API support)
- Anthropic API key: Get a key at: [https://console.anthropic.com/](https://console.anthropic.com/)

## Configuration

The script supports the following environment variables:

- `ANTHROPIC_API_KEY` (required): Your Anthropic API key for Claude AI
- `MAX_TOKENS` (optional): Maximum tokens for AI response (default: 4000, range: 1000-8000)

### API key handling

- Never commit your `ANTHROPIC_API_KEY` to version control
- Store your API key in your shell profile as described in Setup
- Rotate your API key immediately if it's accidentally exposed or committed
- The temporary `ai-script.js` file is automatically deleted after execution for security

## Setup

```bash
# Set your API key
export ANTHROPIC_API_KEY="sk-ant-your-key-here"
echo 'export ANTHROPIC_API_KEY="your-key"' >> ~/.zshrc
source ~/.zshrc

# Fetch latest tags
git fetch --tags

# Make script executable  
chmod +x gen-rn.sh

# Optionally, set custom token limit for current terminal session
export MAX_TOKENS=6000
```

## Usage

### Basic usage

```bash
# Auto-detect previous tag and generate delta
./gen-rn.sh 4.6.4

# Specify both tags to compare (what's new in 4.6.4 vs 4.6.3)
./gen-rn.sh 4.6.4 4.6.3

# Interactive mode - prompts for tag selection
# Detects previous tag and generate delta
./gen-rn.sh

# Help
./gen-rn.sh --help
```

### Custom base comparison with `--from-base`

Use the `--from-base` flag to compare a release against any git reference (branch, tag, or commit):

```bash
# Show everything in 4.6.4 that's not in main
./gen-rn.sh 4.6.4 --from-base main

# Show full content of a release candidate
./gen-rn.sh 4.6.4-rc.1 --from-base main

# Show all changes since a specific older tag
./gen-rn.sh 4.6.4 --from-base 4.5.0

# Compare against a branch
./gen-rn.sh 4.6.4 --from-base release/4.6
```

### Supported release types

The script automatically detects release types based on tag naming:

```bash
# Stable release (production-ready)
./gen-rn.sh 4.6.7                    # → Release type: stable

# Release candidate (pre-release testing)
./gen-rn.sh 4.6.7-rc.1 4.6.6         # → Release type: release-candidate

# Development release (experimental)
./gen-rn.sh 4.6.7-dev.abc123 4.6.7   # → Release type: development
```

### Tag name validation

The script validates all tag names to prevent command injection attacks. Tag names must contain only:
- Letters (a-z, A-Z)
- Numbers (0-9)
- Dots (.)
- Hyphens (-)
- Underscores (_)
- Forward slashes (/)

## Output files

After running, check the `output/` directory:

```text
output/
├── release-notes.md    # Main AI-generated release notes
├── release-data.txt    # Raw git analysis data (for debugging)
└── pr-refs.txt         # PR numbers found in commits
```

> [!NOTE]
> - The `gen-rn/output/` directory is automatically excluded from version control via `.gitignore`
> - The temporary `ai-script.js` file is automatically deleted after execution for security

## Troubleshooting

### "Not in a git repository"

- Run from the root of your XMTP SDK repository

### "ANTHROPIC_API_KEY not set"

- Set your API key: `export ANTHROPIC_API_KEY="your-key"`
- Get a key at: https://console.anthropic.com/

### "Could not compare tags"

- Verify both tags exist: `git tag | grep 4.6.4`  
- Use explicit tag names: `./gen-rn.sh 4.6.4 4.6.3`
