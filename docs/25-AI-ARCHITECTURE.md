# 25. How AI works in Nexus OMS

The OMS decides and records; AI predicts, explains and proposes. Nothing an AI produces changes an order, stock or
a rule without passing the same checks a person's action passes, and every AI action can be traced and undone.

## 1. Three kinds of AI, used for different jobs

| Kind | Used for | How it runs | Why this kind |
|---|---|---|---|
| **Predictive models** (gradient-boosted trees, quantile regression; trained per client) | Delivery dates, phantom stock, store reliability, split risk, return risk, order risk, demand for safety stock | Python service (`nexus-ml`, the existing `supply_chain_ai` reworked), trained nightly from the client's own history; Java calls it over HTTP with a 50 ms budget, with a rule-based fallback | Numbers must be fast, cheap, testable and explainable; an LLM is the wrong tool for a probability |
| **Optimisation** | Choosing locations (cost-to-serve), split avoidance, capacity | Java, inside routing; uses the predictions as inputs | Deterministic and auditable; the same input gives the same answer |
| **Language models** (Claude, through the Anthropic API) | Understanding requests, explaining, drafting rule sets, exception triage, operations questions, customer-service answers | Java `LlmGateway` calls the Messages API with tools; tools are the OMS's own APIs (already exposed by the Nexus MCP server) | Language and judgement; never the source of a number or a stock figure |

Model choice per task (configurable per client):
- `claude-opus-5-5` for multi-step agent work (exception resolution plans, rule drafting).
- `claude-sonnet-5-5` for interactive questions and explanations.
- `claude-haiku-5-5` for high-volume, simple work (classifying a reject reason, summarising a carrier event).

The gateway is provider-neutral: one interface, Anthropic as the default implementation, so a client that requires
another provider or a self-hosted model can plug one in.

`nexus.ai.provider` picks it: `ollama` (default for development and testing: a local model, `qwen2.5-coder:3b` at
`http://localhost:11434`, nothing leaves the machine, cost 0) or `anthropic` (Claude; needs `ANTHROPIC_API_KEY` and
the `NEXUS_AI_MODEL_*` settings set to Claude model ids). `OllamaProvider` translates the Anthropic message shape to
Ollama's chat API and back. A small local model often writes a tool call as JSON text; a reply that is only such a
call, naming a tool that was offered, is read as the call. Answers from a 3B model are weaker than Claude's; the
guardrails (tools for every number, "Not verified" when no tool backs the answer) are the same.

## 2. How a language-model call works

```
user / event ──► Nexus AI service ──► LlmGateway ──► Claude (Messages API)
                     │                     ▲   │
                     │   tool calls ◄──────┘   │  e.g. getOrder, getStockByLocation, simulateRouting,
                     │                         ▼       draftRoutingRule, proposeReallocation
                     │                 OMS APIs (read freely; write only as *proposals*)
                     ▼
               proposal ──► policy check ──► auto-apply (low risk, pre-approved)  or  approval queue
                                                     │
                                         audit log (prompt, tools, result, who approved, undo record)
```

Rules for every call:
1. **Grounding.** The model gets data only through tools that read the live OMS. It is told to answer only from tool results;
   an answer without a tool result behind it is shown as "not verified".
2. **Write as proposals.** Tools that change things (reallocate, hold, cancel, change a rule) create a *proposal* row,
   never a direct change. A proposal is applied by the same service a user's button calls, so permissions, status
   flows and stock checks still apply.
3. **Policy.** Each action type has a risk level and an approval rule per client: auto-apply, auto-apply under limits
   (for example, reallocate orders under 200 dollars), or always ask. Spending limits per agent per day.
4. **Audit and undo.** Every proposal stores the prompt, the tools called, the data seen, the decision, who approved,
   and an undo record (`entity_audit_log` plus a compensating action).
5. **Privacy.** Customer names, emails and addresses are masked before leaving the OMS unless the task needs them
   (customer-service answers); the client can switch to a zero-retention configuration.
6. **Cost and failure.** Timeouts, retries with backoff, a per-client token budget, prompt caching for the long
   system and tool definitions; if the model is unavailable the OMS keeps working on rules.
7. **Evaluation.** Each agent has a test set of real (anonymised) cases with expected outcomes; a release of a prompt
   or model only ships if it scores at least as well as the last one.

## 3. Each AI feature: the problem, the data, the method, the guardrail

| # | Feature | Problem it solves | Data | Method | Guardrail |
|---|---|---|---|---|---|
| 1 | Agent-ready order API (ACP, UCP) | AI shopping assistants can't buy from the client, or promise what can't be delivered | Catalogue, availability service, orders | Protocol endpoints over the order API; webhooks for order events | Same checkout validation, fraud and payment rules as the web store |
| 2 | Availability and promise service | Cancellations from overselling; vague delivery dates | Stock, reservations, capacity, cut-offs, carrier transit times, model 4 | Fast read model, recalculated on every stock event | Answer includes its confidence; low confidence gives a later date, not a refusal |
| 3 | Agent-safe post-purchase changes | Customer agents asking to cancel, re-address, return | Order status, store policy | Policy engine over order changes | Outside policy goes to a person |
| 4 | Delivery-date prediction | Late or over-cautious promises | Past orders: hour, origin, destination, carrier, store load | Quantile model (asymmetric: late costs trust) | Falls back to carrier transit rules |
| 5 | Cost-to-serve sourcing | Cheapest location isn't the cheapest order | Shipping rates, labour per store, split risk, rejection risk, markdown risk | Optimisation in routing with predicted costs | Rule constraints still apply; explanation stored per decision |
| 6 | Split prediction and prevention | Split shipments cost money and annoy customers | Order lines, stock spread, past splits | Classifier, then hold or consolidate | Never delays past the promise date |
| 7 | Phantom stock detection | Stores show stock they don't have: rejections, cancellations | Sales, rejections, counts, receipts, days since last sale | Ranking model, based on published retail research | Lowers routing confidence and asks for a targeted count; never changes on-hand itself |
| 8 | Store reliability score | Some stores reject or fail picks more | Rejections, pick times, staffing hours | Per-store, per-category probability | Feeds routing as a cost; managers see why |
| 9 | Return-risk prediction | Returns cost margin; size problems repeat | Product, size history, customer return history | Classifier at order time | Only advice and routing preference; no customer is refused automatically |
| 10 | Order risk scoring | Fraud and payment failures | Payment signals, address, history | Score plus rules | Holds, never cancels, without approval |
| 11 | Dynamic safety stock | Fixed buffers oversell or hide stock | Demand forecast, record accuracy (7) | Per product and location buffer | Bounds set by the client |
| 12 | Learning routing | Using a store's last unit that it needs tomorrow | Routing outcomes | Lookahead and reinforcement learning, offline only | Runs in simulation; promoted only after it beats rules |
| 13 | Exception agent | Stuck orders found too late, by hand | Exceptions, order history, stock, carrier events | Claude with tools; triage, cause, proposed fix | Proposals and policy (section 2) |
| 14 | Plain-language routing | Business users need IT to change rules | Current rules, order and stock snapshot | Claude drafts a rule set; routing simulator scores it | Simulation result and approval before it goes live |
| 15 | Operations assistant | "Why is this late?", "who rejects most?" takes hours | Read-only tools | Claude with tools | Read-only |
| 16 | Where-is-my-order agent | Customer-service load | Order, shipment, tracking | Claude with tools, customer-safe answers | Changes only within policy (3) |
| 17 | Proactive re-promise | Customers learn of delays last | Tracking events, model 4 | Event rule plus a new prediction | Message templates approved by the client |
| 18 | Store associate assistant | Slow picks, wrong reject reasons | Picklist, locations, reasons | Guided flow; Claude for free-text reasons | Reason codes stay a fixed list |
| 19 | Explain, log, undo | AI decisions nobody can check | All of the above | Audit and undo (section 2) | — |
| 20 | AI scorecard | Proving value to clients | Order replay | Replay the same orders with and without AI; compare cost, splits, on-time, rejections | Shown per client, on their own orders |

## 4. Order of work

1. Foundations: `LlmGateway`, proposals and approval queue, policy, audit and undo, evaluation harness (19, 20).
2. Phantom stock and store reliability (7, 8): the biggest cause of store rejections.
3. Delivery-date prediction and the availability service (4, 2).
4. Exception agent and plain-language routing (13, 14).
5. ACP and UCP (1, 3).
6. The rest as client demand appears.
