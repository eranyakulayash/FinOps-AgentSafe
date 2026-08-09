# FARS Framework Specification

## Financial Agent Reliability Score (FARS)

$$\text{FARS} = w_1 \cdot S_{\text{inv}} + w_2 \cdot S_{\text{auth}} + w_3 \cdot S_{\text{esc}} + w_4 \cdot S_{\text{rec}} + w_5 \cdot S_{\text{audit}}$$

## Default Component Weights (`fars-weights.yml`)

- $w_1$ (Financial Integrity): 0.25
- $w_2$ (Authorization Compliance): 0.20
- $w_3$ (Human Escalation F1): 0.20
- $w_4$ (Failure Recovery Rate): 0.20
- $w_5$ (Audit Completeness): 0.15

Weights must sum to 1.0. Configuration is externalized in `classpath:fars-weights.yml`.
