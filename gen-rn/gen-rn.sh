#!/bin/bash

# XMTP SDK Release Notes Generator
# Generate AI-powered release notes for XMTP SDK releases using Claude

set -e

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="./output"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_header() {
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

# Function to check prerequisites
check_prerequisites() {
    print_header "Checking Prerequisites"
    
    # Check if we're in a git repository
    if ! git rev-parse --git-dir > /dev/null 2>&1; then
        print_error "Not in a git repository. Please run this from an XMTP SDK repository."
        exit 1
    fi
    
    # Check for Node.js (for the AI script)
    if ! command -v node &> /dev/null; then
        print_error "Node.js is required but not installed."
        echo "Please install Node.js from https://nodejs.org/"
        exit 1
    fi
    
    # Check for API key
    if [ -z "$ANTHROPIC_API_KEY" ]; then
        print_error "ANTHROPIC_API_KEY environment variable is not set."
        echo "Please set your API key: export ANTHROPIC_API_KEY=\"your-key-here\""
        exit 1
    fi
    
    # Check for jq (optional but recommended)
    if ! command -v jq &> /dev/null; then
        print_warning "jq is recommended for better output formatting."
        echo "Install with: brew install jq (macOS) or sudo apt install jq (Ubuntu)"
    fi
    
    print_success "Prerequisites check completed"
}

# Function to get tags and setup environment
setup_environment() {
    print_header "Setting Up Environment"
    
    # Create output directory
    mkdir -p "$OUTPUT_DIR"
    
    # Get available tags
    echo "Available git tags:"
    echo "Recent tags:"
    git tag --sort=-version:refname | grep -E "^[0-9]" | head -10
    echo ""
    echo "Legacy tags:"
    git tag --sort=-version:refname | grep "^v" | head -5
    echo ""
    
    # Get current tag or prompt user
    if [ "$1" != "" ]; then
        TAG_NAME="$1"
    else
        echo "Enter the tag name to generate release notes for:"
        read -r TAG_NAME
        
        if [ "$TAG_NAME" = "latest" ]; then
            TAG_NAME=$(git tag --sort=-version:refname | head -n 1)
            if [ -z "$TAG_NAME" ]; then
                print_error "No tags found in repository"
                exit 1
            fi
        fi
    fi
    
    # Get previous tag with smart detection (from working local-test-runner.sh)
    if [ "$2" != "" ]; then
        PREVIOUS_TAG="$2"
    else
        # Try to find the previous tag more intelligently
        
        # First, determine if current tag has 'v' prefix
        if [[ "$TAG_NAME" == v* ]]; then
            # Look for other v-prefixed tags
            PREVIOUS_TAG=$(git tag --sort=-version:refname | grep "^v" | grep -v "$TAG_NAME" | head -n 1)
        else
            # Look for non-v-prefixed tags in the same version series
            VERSION_SERIES=$(echo "$TAG_NAME" | cut -d. -f1-2)  # e.g., "4.6" from "4.6.4"
            
            # Try to find previous tag in same series first
            PREVIOUS_TAG=$(git tag | grep "^${VERSION_SERIES}\." | sort -V | grep -B1 "^${TAG_NAME}$" | head -n1)
            
            # If no same-series tag found, try broader search
            if [ -z "$PREVIOUS_TAG" ]; then
                MAJOR_VERSION=$(echo "$TAG_NAME" | cut -d. -f1)  # e.g., "4" from "4.6.4"
                PREVIOUS_TAG=$(git tag | grep "^${MAJOR_VERSION}\." | sort -V | grep -B1 "^${TAG_NAME}$" | head -n1)
            fi
            
            # If still no tag, fall back to any non-v tag
            if [ -z "$PREVIOUS_TAG" ]; then
                PREVIOUS_TAG=$(git tag --sort=-version:refname | grep -v "^v" | grep -v "$TAG_NAME" | head -n 1)
            fi
        fi
        
        if [ -z "$PREVIOUS_TAG" ]; then
            PREVIOUS_TAG=$(git rev-list --max-parents=0 HEAD)
            print_warning "No previous tag found, comparing against first commit"
        else
            echo "Using previous tag: $PREVIOUS_TAG"
        fi
    fi
    
    # Determine release type
    if [[ "$TAG_NAME" == *"rc"* ]]; then
        RELEASE_TYPE="release-candidate"
    elif [[ "$TAG_NAME" == *"dev"* ]]; then
        RELEASE_TYPE="development"
    else
        RELEASE_TYPE="stable"
    fi
    
    echo ""
    print_success "Environment configured:"
    echo "  Repository: $(basename $(git rev-parse --show-toplevel))"
    echo "  Current tag: $TAG_NAME"
    echo "  Previous tag: $PREVIOUS_TAG"
    echo "  Release type: $RELEASE_TYPE"
    echo "  Output directory: $OUTPUT_DIR"
}

# Function to gather release information
gather_release_info() {
    print_header "Gathering Release Information"
    
    local info_file="$OUTPUT_DIR/release-data.txt"
    
    echo "=== XMTP SDK Release Analysis ===" > "$info_file"
    echo "Repository: $(basename $(git rev-parse --show-toplevel))" >> "$info_file"
    echo "Current tag: $TAG_NAME" >> "$info_file"
    echo "Previous tag: $PREVIOUS_TAG" >> "$info_file"
    echo "Release type: $RELEASE_TYPE" >> "$info_file"
    echo "Generated: $(date)" >> "$info_file"
    echo "" >> "$info_file"
    
    # Get commit messages between tags
    echo "=== COMMIT MESSAGES ===" >> "$info_file"
    if git rev-list "$PREVIOUS_TAG..$TAG_NAME" &>/dev/null; then
        git log "$PREVIOUS_TAG..$TAG_NAME" --pretty=format:"- %s (%an, %ad)" --date=short >> "$info_file" || true
    else
        print_warning "Could not compare tags, using last 10 commits"
        git log -10 --pretty=format:"- %s (%an, %ad)" --date=short >> "$info_file"
    fi
    echo -e "\n" >> "$info_file"
    
    # Get changed files with stats
    echo "=== CHANGED FILES ===" >> "$info_file"
    if git rev-list "$PREVIOUS_TAG..$TAG_NAME" &>/dev/null; then
        git diff --name-status "$PREVIOUS_TAG..$TAG_NAME" >> "$info_file" 2>/dev/null || true
    else
        git diff --name-status HEAD~10..HEAD >> "$info_file" 2>/dev/null || true
    fi
    echo -e "\n" >> "$info_file"
    
    # Get detailed diff for key files
    echo "=== KEY FILE CHANGES ===" >> "$info_file"
    for file in package.json package-lock.json Cargo.toml Cargo.lock setup.py requirements.txt build.gradle app/build.gradle README.md CHANGELOG.md; do
        if [ -f "$file" ]; then
            if git rev-list "$PREVIOUS_TAG..$TAG_NAME" &>/dev/null; then
                if git diff --name-only "$PREVIOUS_TAG..$TAG_NAME" | grep -q "^$file$"; then
                    echo "Changes in $file:" >> "$info_file"
                    git diff "$PREVIOUS_TAG..$TAG_NAME" -- "$file" >> "$info_file" 2>/dev/null || true
                    echo -e "\n" >> "$info_file"
                fi
            fi
        fi
    done
    
    # Get PR information if available
    echo "=== PULL REQUEST REFERENCES ===" >> "$info_file"
    if git rev-list "$PREVIOUS_TAG..$TAG_NAME" &>/dev/null; then
        git log "$PREVIOUS_TAG..$TAG_NAME" --pretty=format:"%s" | grep -oE "#[0-9]+" | sort -u > "$OUTPUT_DIR/pr-refs.txt" 2>/dev/null || touch "$OUTPUT_DIR/pr-refs.txt"
    else
        git log -10 --pretty=format:"%s" | grep -oE "#[0-9]+" | sort -u > "$OUTPUT_DIR/pr-refs.txt" 2>/dev/null || touch "$OUTPUT_DIR/pr-refs.txt"
    fi
    
    if [ -s "$OUTPUT_DIR/pr-refs.txt" ]; then
        echo "PR numbers found in commits:" >> "$info_file"
        cat "$OUTPUT_DIR/pr-refs.txt" >> "$info_file"
    else
        echo "No PR references found in commit messages" >> "$info_file"
    fi
    
    local filesize=$(wc -c < "$info_file")
    print_success "Release information gathered ($filesize bytes)"
    echo "  Data file: $info_file"
    echo "  PR refs: $OUTPUT_DIR/pr-refs.txt"
}

# Function to generate AI release notes
generate_ai_release_notes() {
    print_header "Generating AI Release Notes"
    
    local info_file="$OUTPUT_DIR/release-data.txt"
    local ai_script="$OUTPUT_DIR/ai-script.js"
    
    # Create the AI generation script
    cat > "$ai_script" << 'EOF'
const fs = require('fs');

async function generateReleaseNotes() {
    const releaseInfo = fs.readFileSync('./output/release-data.txt', 'utf8');
    
    // Truncate if too long (API limits)
    const maxLength = 50000;
    const truncatedInfo = releaseInfo.length > maxLength 
        ? releaseInfo.substring(0, maxLength) + "\n\n[Content truncated due to length...]"
        : releaseInfo;
    
    const prompt = `Analyze the following XMTP SDK release information and generate comprehensive release notes.

CONTEXT:
- XMTP is a messaging protocol for web3 applications
- This is for SDK releases used by developers building messaging applications
- The audience includes: engineers making releases, technical writers, and cross-SDK comparison
- Focus on developer-facing changes, new features, bug fixes, and breaking changes

RELEASE INFORMATION:
${truncatedInfo}

Please generate release notes that include:

1. **Release Summary** (2-3 sentences about what this release contains)

2. **New Features** (if any)
- List new capabilities added
- Explain developer benefits and use cases

3. **Improvements & Enhancements** (if any)
- Performance improvements
- Developer experience enhancements
- API improvements

4. **Bug Fixes** (if any)
- Issues resolved
- Stability improvements

5. **Breaking Changes** (if any)
- API changes that require developer action
- Clear migration guidance

6. **Dependencies & Infrastructure** (if any)
- Dependency updates
- Build or tooling changes
- Library version updates

7. **Developer Notes** (if any)
- Important implementation details
- Testing recommendations
- Known limitations

FORMAT REQUIREMENTS:
- Use clear markdown formatting
- Be specific about technical changes but explain them in plain English
- Focus on changes that matter to developers integrating this SDK
- If certain sections don't apply, omit those sections
- Include code examples for breaking changes where helpful
- Avoid overly technical git details unless relevant to SDK users`;

    try {
        console.log("🤖 Generating release notes with AI...");
        console.log(`📄 Prompt length: ${prompt.length} characters`);
        
        const response = await fetch("https://api.anthropic.com/v1/messages", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "x-api-key": process.env.ANTHROPIC_API_KEY,
                "anthropic-version": "2023-06-01",
            },
            body: JSON.stringify({
                model: "claude-sonnet-4-20250514",
                max_tokens: 1000,
                messages: [
                    { role: "user", content: prompt }
                ],
            })
        });

        console.log(`📡 API Response status: ${response.status}`);

        if (!response.ok) {
            const errorText = await response.text();
            throw new Error(`HTTP error! status: ${response.status}, body: ${errorText}`);
        }

        const data = await response.json();
        const releaseNotes = data.content
            .map(item => (item.type === "text" ? item.text : ""))
            .filter(Boolean)
            .join("\n");

        // Save release notes
        fs.writeFileSync('./output/release-notes.md', releaseNotes);
        console.log("✅ Release notes generated successfully");
        
    } catch (error) {
        console.error('❌ Error generating release notes:', error.message);
        
        const fallbackNotes = `# Release Notes for ${process.env.TAG_NAME || 'Unknown Tag'}

**⚠️ AI generation failed - please create release notes manually**

Error: ${error.message}

## Changes in this release
See commit history between ${process.env.PREVIOUS_TAG || 'unknown'} and ${process.env.TAG_NAME || 'unknown'}

## Raw release information:
\`\`\`
${truncatedInfo.substring(0, 2000)}
\`\`\`

---
*Note: This is fallback content. Please review git history and create proper release notes.*
`;
        fs.writeFileSync('./output/release-notes.md', fallbackNotes);
        console.log("📁 Fallback release notes created");
        process.exit(1);
    }
}

// Set environment variables for the script
process.env.TAG_NAME = process.argv[2] || process.env.TAG_NAME;
process.env.PREVIOUS_TAG = process.argv[3] || process.env.PREVIOUS_TAG;
process.env.RELEASE_TYPE = process.argv[4] || process.env.RELEASE_TYPE;

generateReleaseNotes();
EOF

    # Set environment variables for the Node.js script
    export TAG_NAME="$TAG_NAME"
    export PREVIOUS_TAG="$PREVIOUS_TAG"
    export RELEASE_TYPE="$RELEASE_TYPE"
    
    # Run the AI generation
    if node "$ai_script" "$TAG_NAME" "$PREVIOUS_TAG" "$RELEASE_TYPE"; then
        print_success "AI release notes generated successfully"
    else
        print_warning "AI generation failed, but fallback content was created"
    fi
}

# Function to display results
display_results() {
    print_header "Release Notes Generated"
    
    echo "📁 Generated files in $OUTPUT_DIR/:"
    ls -la "$OUTPUT_DIR/" | grep -v "^d" | awk '{print "  " $9 " (" $5 " bytes)"}'
    echo ""
    
    if [ -f "$OUTPUT_DIR/release-notes.md" ]; then
        echo "📝 Release Notes Preview:"
        echo "========================"
        head -30 "$OUTPUT_DIR/release-notes.md"
        echo ""
        echo "[... view complete release notes in $OUTPUT_DIR/release-notes.md ...]"
        echo ""
    fi
    
    print_success "Release notes generation completed!"
    echo ""
    echo "📋 Next steps:"
    echo "1. Review the release notes: $OUTPUT_DIR/release-notes.md"
    echo "2. Edit/customize as needed for your release"
    echo "3. Use the notes in your GitHub release or documentation"
    echo ""
    echo "📊 For debugging or customization:"
    echo "- Raw data analyzed: $OUTPUT_DIR/release-data.txt"
    echo "- PR references found: $OUTPUT_DIR/pr-refs.txt"
}

# Main execution
main() {
    echo "🚀 XMTP SDK Release Notes Generator"
    echo "==================================="
    echo ""
    
    check_prerequisites
    setup_environment "$1" "$2"
    gather_release_info
    generate_ai_release_notes
    display_results
}

# Script usage help
if [ "$1" = "--help" ] || [ "$1" = "-h" ]; then
    echo "XMTP SDK Release Notes Generator"
    echo ""
    echo "Generates AI-powered release notes for XMTP SDK releases using git history analysis."
    echo ""
    echo "Usage:"
    echo "  $0 [tag_name] [previous_tag]"
    echo "  $0 --help|-h"
    echo ""
    echo "Examples:"
    echo "  $0                     # Interactive mode - will prompt for tag"
    echo "  $0 4.6.4               # Generate notes for v4.6.4 (auto-detect previous)"
    echo "  $0 4.6.4 4.6.3         # Generate notes comparing 4.6.4 to 4.6.3"
    echo "  $0 latest              # Generate notes for most recent tag"
    echo ""
    echo "Requirements:"
    echo "  - Git repository with release tags"
    echo "  - Node.js installed"
    echo "  - ANTHROPIC_API_KEY environment variable set"
    echo "  - Internet connection (for AI API)"
    echo ""
    echo "Output:"
    echo "  - output/release-notes.md    # Main AI-generated release notes"
    echo "  - output/release-data.txt    # Raw git analysis data"
    echo "  - output/pr-refs.txt         # PR references found"
    echo ""
    exit 0
fi

# Run the main function
main "$@"