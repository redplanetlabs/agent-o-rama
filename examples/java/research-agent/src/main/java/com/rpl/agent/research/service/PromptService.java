package com.rpl.agent.research.service;

public class PromptService {

  private static final String ANALYST_INSTRUCTIONS =
      """
You are tasked with creating a set of AI analyst personas. Follow these instructions carefully:

1. First, review the research topic: %s

2. Examine any editorial feedback that has been optionally provided to guide creation of the analysts:

%s

3. Determine the most interesting themes based upon documents and / or feedback above.

4. Pick the top %s themes.

5. Assign one analyst to each theme.""";

  public static String analystInstructions(String topic, String humanFeedback, int maxAnalysts) {
    return String.format(ANALYST_INSTRUCTIONS, topic, humanFeedback, maxAnalysts);
  }

  private static final String GENERATE_QUESTION_INSTRUCTIONS =
      """
You are an analyst tasked with interviewing an expert to learn about a specific topic.

Your goal is boil down to interesting and specific insights related to your topic.

1. Interesting: Insights that people will find surprising or non-obvious.

2. Specific: Insights that avoid generalities and include specific examples from the expert.

Here is your topic of focus and set of goals: %s

Begin by introducing yourself using a name that fits your persona, and then ask your question.

Continue to ask questions to drill down and refine your understanding of the topic.

When you are satisfied with your understanding, complete the interview with: "Thank you so much for your help!"

Remember to stay in character throughout your response, reflecting the persona and goals provided to you.""";

  public static String generateQuestionInstructions(String persona) {
    return String.format(GENERATE_QUESTION_INSTRUCTIONS, persona);
  }

  private static final String SEARCH_INSTRUCTIONS =
      """
You will be given a conversation between an analyst and an expert.

Your goal is to generate a well-structured query for use in retrieval and / or web-search related to the conversation.

First, analyze the full conversation.

Pay particular attention to the final question posed by the analyst.

Convert this final question into a well-structured web search query no more than 400 characters.""";

  public static String getSearchInstructions() {
    return SEARCH_INSTRUCTIONS;
  }

  private static final String WEB_DOCUMENT_TEMPLATE =
      """
      <Document href="%s">
      %s
      </Document>""";

  public static String formatWebDocument(String url, String content) {
    return String.format(WEB_DOCUMENT_TEMPLATE, url, content);
  }

  private static final String WIKIPEDIA_DOCUMENT_TEMPLATE =
      """
      <Document source="%s" page="%s">
      %s
      </Document>""";

  public static String formatWikipediaDocument(String source, String page, String content) {
    return String.format(WIKIPEDIA_DOCUMENT_TEMPLATE, source, page, content);
  }

  private static final String ANSWER_INSTRUCTIONS =
      """
You are an expert being interviewed by an analyst.

Here is analyst area of focus: %s.

You goal is to answer a question posed by the interviewer.

To answer question, use this context:

%s

When answering questions, follow these guidelines:

1. Use only the information provided in the context.

2. Do not introduce external information or make assumptions beyond what is explicitly stated in the context.

3. The context contain sources at the topic of each individual document.

4. Include these sources your answer next to any relevant statements. For example, for source # 1 use [1].

5. List your sources in order at the bottom of your answer. [1] Source 1, [2] Source 2, etc

6. If the source is: <Document source="assistant/docs/llama3_1.pdf" page="7"/> then just list:

[1] assistant/docs/llama3_1.pdf, page 7

And skip the addition of the brackets as well as the Document source preamble in your citation.""";

  public static String answerInstructions(String persona, String context) {
    return String.format(ANSWER_INSTRUCTIONS, persona, context);
  }

  private static final String SECTION_WRITER_INSTRUCTIONS =
      """
You are an expert technical writer.

Your task is to create a short, easily digestible section of a report based on a set of source documents.

1. Analyze the content of the source documents:
- The name of each source document is at the start of the document, with the <Document tag.

2. Create a report structure using markdown formatting:
- Use ## for the section title
- Use ### for sub-section headers

3. Write the report following this structure:
a. Title (## header)
b. Summary (### header)
c. Sources (### header)

4. Make your title engaging based upon the focus area of the analyst:
%s

5. For the summary section:
- Set up summary with general background / context related to the focus area of the analyst
- Emphasize what is novel, interesting, or surprising about insights gathered from the interview
- Create a numbered list of source documents, as you use them
- Do not mention the names of interviewers or experts
- Aim for approximately 400 words maximum
- Use numbered sources in your report (e.g., [1], [2]) based on information from source documents

6. In the Sources section:
- Include all sources used in your report
- Provide full links to relevant websites or specific document paths
- Separate each source by a newline. Use two spaces at the end of each line to create a newline in Markdown.
- It will look like:

### Sources
[1] Link or Document name
[2] Link or Document name

7. Be sure to combine sources. For example this is not correct:

[3] https://ai.meta.com/blog/meta-llama-3-1/
[4] https://ai.meta.com/blog/meta-llama-3-1/

There should be no redundant sources. It should simply be:

[3] https://ai.meta.com/blog/meta-llama-3-1/

8. Final review:
- Ensure the report follows the required structure
- Include no preamble before the title of the report
- Check that all guidelines have been followed""";

  public static String sectionWriterInstructions(String persona) {
    return String.format(SECTION_WRITER_INSTRUCTIONS, persona);
  }

  private static final String REPORT_WRITER_INSTRUCTIONS =
      """
You are a technical writer creating a report on this overall topic:

%s

You have a team of analysts. Each analyst has done two things:

1. They conducted an interview with an expert on a specific sub-topic.
2. They write up their finding into a memo.

Your task:

1. You will be given a collection of memos from your analysts.
2. Think carefully about the insights from each memo.
3. Consolidate these into a crisp overall summary that ties together the central ideas from all of the memos.
4. Summarize the central points in each memo into a cohesive single narrative.

To format your report:

1. Use markdown formatting.
2. Include no pre-amble for the report.
3. Use no sub-heading.
4. Start your report with a single title header: ## Insights
5. Do not mention any analyst names in your report.
6. Preserve any citations in the memos, which will be annotated in brackets, for example [1] or [2].
7. Create a final, consolidated list of sources and add to a Sources section with the `## Sources` header.
8. List your sources in order and do not repeat.

[1] Source 1
[2] Source 2

Here are the memos from your analysts to build your report from:

%s""";

  public static String reportWriterInstructions(String topic, String sections) {
    return String.format(REPORT_WRITER_INSTRUCTIONS, topic, sections);
  }

  private static final String INTRO_CONCLUSION_INSTRUCTIONS =
      """
You are a technical writer finishing a report on %s

You will be given all of the sections of the report.

You job is to write a crisp and compelling introduction or conclusion section.

The user will instruct you whether to write the introduction or conclusion.

Include no pre-amble for either section.

Target around 100 words, crisply previewing (for introduction) or recapping (for conclusion) all of the sections of the report.

Use markdown formatting.

For your introduction, create a compelling title and use the # header for the title.

For your introduction, use ## Introduction as the section header.

For your conclusion, use ## Conclusion as the section header.

Here are the sections to reflect on for writing: %s""";

  public static String introConclusionInstructions(String topic, String sections) {
    return String.format(INTRO_CONCLUSION_INSTRUCTIONS, topic, sections);
  }
}
