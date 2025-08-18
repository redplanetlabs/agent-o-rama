# Research Agent Example

A comprehensive multi-agent research system built with agent-o-rama framework that automatically conducts in-depth research on any topic by creating specialized AI analysts, conducting expert interviews, and generating structured reports.

## Features

- **Dynamic Analyst Creation**: Automatically generates specialized AI analyst personas for different aspects of your research topic
- **Expert Interviews**: Each analyst conducts multi-turn conversations with simulated experts to gather insights  
- **Multi-Source Research**: Combines web search (via Tavily) and Wikipedia research for comprehensive coverage
- **Human Feedback Loop**: Allows you to review and refine the generated analysts before research begins
- **Structured Report Generation**: Produces professional markdown reports with proper citations and sources
- **Parallel Processing**: Utilizes agent-o-rama's distributed processing for efficient concurrent research

## How It Works

The research process follows these stages:

1. **Topic Analysis**: Analyzes your research topic to identify key themes
2. **Analyst Generation**: Creates 4 specialized AI analysts, each focused on different aspects
3. **Human Feedback**: Presents the analysts for your review and refinement
4. **Interview Process**: Each analyst conducts 2 rounds of expert interviews
5. **Evidence Gathering**: Performs web and Wikipedia searches to support findings
6. **Report Writing**: Generates individual sections and combines into final report
7. **Final Assembly**: Creates introduction, conclusion, and consolidated sources

## Prerequisites

- Java 21 or newer
- Maven 3.6+
- OpenAI API key
- Tavily API key (for web search)

## Setup

1. **Clone and navigate to the example**:
   ```bash
   cd examples/java/research-agent
   ```

2. **Set required environment variables**:
   ```bash
   export OPENAI_API_KEY=your_openai_api_key_here
   export TAVILY_API_KEY=your_tavily_api_key_here
   ```

3. **Install dependencies**:
   ```bash
   mvn clean install
   ```

## Running the Example

Start the research agent:
```bash
mvn exec:java
```

Or with custom JVM options:
```bash
mvn exec:java -Dexec.args="-Xmx4g"
```

## Usage Example

```
Starting Research Agent...
Enter a topic: Artificial Intelligence in Healthcare

Do you have any feedback on this set of analysts? Answer 'yes' or 'no'.

Analyst{name='Dr. Sarah Chen', role='Clinical AI Implementation Specialist', 
affiliation='Stanford Medical Center', description='Focuses on practical deployment...'}
...

>> no

Research completed! Here is your comprehensive report:
================================================================

# The Future of AI in Healthcare: Transforming Patient Care Through Technology

## Introduction
Artificial intelligence is revolutionizing healthcare delivery...
```

## Configuration

You can customize the research process by modifying `ResearchOptions`:

- **Max Analysts**: Number of specialist analysts (default: 4)
- **Max Turns**: Interview rounds per analyst (default: 2)

## Architecture

The system consists of these key components:

### Agent Nodes
- **create-analysts**: Generates AI analyst personas using JSON schema
- **feedback**: Handles human feedback on analysts  
- **questions**: Distributes analysts for parallel interviews
- **generate-question**: Creates interview questions
- **search-web/search-wikipedia**: Gathers supporting evidence
- **agg-research**: Aggregates research findings
- **write-section**: Generates individual section reports
- **begin-report**: Coordinates final report assembly

### Services
- **WikipediaService**: Wikipedia API integration
- **TavilySearchService**: Web search functionality  
- **PromptService**: All prompt templates and instructions

### Models
- **Analyst**: Analyst persona data structure
- **ResearchOptions**: Configuration parameters
- **WikipediaDocument**: Wikipedia content wrapper

## Troubleshooting

**"OPENAI_API_KEY not set" error**:
- Ensure you've exported your OpenAI API key as an environment variable

**"TAVILY_API_KEY not set" error**:  
- Get a free API key from https://tavily.com and export it

**Out of memory errors**:
- Increase JVM heap size: `-Dexec.args="-Xmx4g"`
- Research agents can use significant memory for large topics

**Network timeouts**:
- Check your internet connection
- Some research topics may require more API calls

## Example Topics

Try these research topics to see the system in action:

- "Climate change adaptation strategies"
- "Quantum computing applications in finance"  
- "The impact of remote work on urban planning"
- "Biotechnology advances in sustainable agriculture"
- "Ethical implications of autonomous vehicles"

## Customization

To customize the research process:

1. **Modify Prompts**: Edit templates in `PromptService`
2. **Add Search Sources**: Extend search capabilities in service classes  
3. **Adjust Report Format**: Modify report generation nodes
4. **Change Analyst Count**: Update default in `ResearchOptions`

## Performance Notes

- Research typically takes 3-10 minutes depending on topic complexity
- Network latency affects overall completion time
- The system makes multiple API calls to OpenAI and Tavily
- Consider API rate limits for your subscription tiers

## Dependencies

- **agent-o-rama**: Core agent framework
- **LangChain4j**: AI model integration and tool calling
- **Tavily**: Web search capabilities
- **Jackson**: JSON processing
- **Apache HTTP**: Web requests