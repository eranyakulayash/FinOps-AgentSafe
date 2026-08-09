# External Provider Integration Guide

## Overview
FinOps-AgentSafe provides adapter skeletons for Google Gemini, OpenAI, and Anthropic Claude.

> **Status Notice:** External-model evaluation is currently **OPTIONAL / EXPERIMENTAL**. Standard automated tests (`mvn test`) use `MockModelAdapter` and require zero network access or API credentials.

## Environment Secret Setup
Create a local `.env` file (or set environment variables):

```bash
export GEMINI_API_KEY="your_gemini_api_key"
export OPENAI_API_KEY="your_openai_api_key"
export ANTHROPIC_API_KEY="your_anthropic_api_key"
```

## Credential Missing Behavior
If an external provider is selected via CLI without setting the corresponding environment key:
- Output: `[CLI ERROR] PROVIDER_NOT_CONFIGURED: Model provider 'gemini' is not configured or missing environment API key.`
- The engine exits cleanly without crashing or prompting interactively for credentials.
