# Glossary

| Term | Meaning |
|---|---|
| **PAN** | Income-tax ID of a person/company. The system's key for a client. Stored encrypted. |
| **Guardian PAN** | The PAN of a minor's guardian. Used only when the client has no PAN of their own. |
| **Finesse** | The source system (from Aug 2026) for MF brokerage and the client master. Replaced Investwell. |
| **Finesse client code** | Finesse's ID for an account (e.g. FC00237). Several codes can belong to one PAN. |
| **Finesse client master** | Export (ClientMasterReport) saying which PAN / guardian PAN each code has. Owned by the Ops team. |
| **Master MIS** | The business master: per PAN, the RM, sub broker, referral partner and %. Owned by MIS/HO. |
| **RM / RD** | Relationship Manager (owns the client) / Regional Director (heads a region). |
| **Book** | An RM's set of clients in one branch. One person can have two books (two branches). |
| **HOUSE book** | A non-person RM such as HEAD OFFICE or STAFF: holds clients with no person as RM. Sits in a branch and reports like an RM; cannot be an RD or a partner's RM. Added on Org → "Add house book". |
| **SB — Sub broker** | Partner who takes a % of the client's net brokerage first. |
| **RF — Referral partner** | Partner who takes a % of what is left after the sub broker. |
| **POSP** | Insurance point-of-sale partner (insurance comes later). |
| **Net / GST / MF trail** | Net = brokerage excluding GST. GST = 18% of net. MF trail = net + GST. |
| **Period / month** | A calendar month per product. OPEN → PROCESSED → LOCKED. |
| **Process** | Splitting every line of a month between partners and Epsilon. |
| **Lock** | FINANCE freezes a month. After that, corrections go into the next open month. |
| **Adjustment** | A correction line for a locked month, posted in the next open month. |
| **END file** | The monthly Excel for finance: Data sheet, Summary, Sub Broker Summary. |
| **Not in Master MIS** | A client in the brokerage file whose PAN (or guardian PAN / name) has no RM in Master MIS. Blocks Process. |
| **Alias** | A remembered spelling: "this name in a file means that RM / partner". |
| **Dated history** | Every change has a "from" date; the old value stays for the months before. |
| **Rate card** | A partner's % for each product (MF, Equity, FD…), from a date. Its clients follow it unless they have their own %. |
| **Client exception** | A client's own % with a partner, different from the partner's rate card. Set by hand on the Clients screen. |
| **Branch rule** | A partner for every client of a branch (e.g. one sub-broker for all clients of a branch), paid first. Set on the Org screen. |
| **Pending partner** | A partner whose approval is under process: linked to its clients but paid nothing until made Active. |
| **Reference** | 8 characters (e.g. `7K2MQX4P`) given to every request and job. Quote it when something fails: it finds the job, the error and the log lines. |
| **Activity** | The screen listing uploads, processing, saves, locks, downloads and errors — who, when, result. |
