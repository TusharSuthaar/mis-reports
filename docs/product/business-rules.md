# Business rules

Every rule the system applies to money or to who-gets-what. **Each row names the test that proves it** — `GuardrailsTest`
fails the build if a named test does not exist. Changing a rule = change the row, the code and the test together.

## Who a client is

| ID | Rule | Example | Proved by |
|---|---|---|---|
| R-01 | A client is one **PAN**. Several Finesse codes of one person add up into one row. | Two accounts of the same PAN → one line, amounts added | `UploadIT.codesFindTheirClientByPanOrGuardianPanAndTheRestBlockProcess` |
| R-02 | A client **with its own PAN** is mapped only by that PAN. If that PAN is not in Master MIS it is **highlighted**, even if the guardian's PAN or the name is there. | Minor with own PAN not in master → highlighted | `UploadIT.codesFindTheirClientByPanOrGuardianPanAndTheRestBlockProcess`, `UploadIT.aClientWithoutPanIsMatchedByItsExactNameInMasterMis` |
| R-03 | A client **without a PAN** is mapped by its **guardian's PAN**; failing that, by its exact name among Master MIS clients that have no PAN (only if one such client). | Minor without PAN → guardian's RM and partners | `UploadIT.aClientWithoutPanIsMatchedByItsExactNameInMasterMis` |
| R-04 | A PAN is stored encrypted. It can be searched in full and corrected only through the app (audited; refused if another client has it). | Search "abcde1234f" finds the client | `ClientPanIT.aPanIsCorrectedAndAudited`, `ClientPanIT.aClientIsFoundByItsFullPanInAnyCase` |

## Brokerage in (Finesse)

| ID | Rule | Example | Proved by |
|---|---|---|---|
| R-10 | The Finesse "Brokerage Amount" is **net of GST**. GST = 18% of it; MF trail = net + GST. | Net 100 → GST 18 → trail 118 | `FinesseBrokerageParserTest.addsUpEachCodesRowsAndWorksOutGstAt18Percent` |
| R-11 | The file's Total row must equal its rows within ₹1 (rounding is noted); otherwise the file is refused. | Off by 0.04 → note; off by 2 → refused | `FinesseBrokerageParserTest.aTotalOffByRoundingIsANoteButOffByMoreIsAnError` |
| R-12 | A file whose brokerage is mostly for another month is refused. Late rows from earlier months count in the month uploaded. | Sep file uploaded as Aug → refused | `UploadIT.aFileThatDoesNotAddUpOrIsForAnotherMonthIsRejected` |
| R-13 | RM, sub broker and referral come **only from Master MIS**, never from the brokerage file. | File says "Suspense RM" → ignored | `ShareIT.theMasterAddsClientsByPanOrByNameAndSetsTheirRm` |
| R-14 | Clients not in Master MIS: the month **can be saved but not processed** until each has an RM. | 1 missing → Process blocked | `UploadIT.codesFindTheirClientByPanOrGuardianPanAndTheRestBlockProcess` |
| R-15 | The same file cannot be uploaded twice (unless the first was discarded). A new file for the month replaces the old one; old lines are voided, never deleted. | Re-upload → old upload superseded | `UploadIT.theSameFileCannotBeUploadedTwiceButADiscardedOneCan`, `UploadIT.aCorrectedFileReplacesTheMonthAndOldLinesAreVoidedNotDeleted` |

## Master MIS (who gets what)

| ID | Rule | Example | Proved by |
|---|---|---|---|
| R-20 | Master MIS rows are keyed by PAN (by name when there is no PAN). Clients it has and the app does not are added; RMs and partners apply from a date chosen at upload. | New PAN in master → new client with its RM | `ShareIT.theMasterAddsClientsByPanOrByNameAndSetsTheirRm` |
| R-21 | One RM with two books (same name, two branches) is told apart by the master's BRANCH column. A row whose branch has no book of its own goes to the RM's general book: the one book in a branch the master never names for that RM; otherwise it is flagged. | KASH in Delhi vs Yamuna Nagar; an RM with a book only for branch C: rows of branches A and B go to his general book | `ShareIT.theBranchPicksBetweenTwoBooksOfTheSameRm`, `ShareIT.rowsOfBranchesWithoutTheirOwnBookGoToTheRmsGeneralBook` |
| R-22 | An RM name the app does not know can be mapped to an RM once; it is remembered for every later upload. | Old name → HEAD OFFICE | `ShareIT.anUnknownRmSpellingCanBeMappedToAnRmOnce` |
| R-23 | Only clients with a partner share. In a master **without** partner sheets, a partner with a blank % is kept at 0% (with sheets, see R-54). | Partner, blank % → 0% | `SharePlanTest.missingPercentMeansZeroAndBlankTypeIsTakenFromOtherRows` |
| R-24 | A client can have one sub broker **and** one referral partner. | Both on one client | `ShareIT.aSubBrokerAndAReferralPartnerAreKeptSeparately` |
| R-25 | A duplicate partner (another spelling) is merged: its clients and RM links move; the spelling is remembered. | AGRAWAL merged into AGARWAL | `ShareIT.aDuplicatePartnerIsMergedIntoTheRightOne`, `SharePlanTest.aMergedSpellingGoesToThePartnerItWasMergedInto` |
| R-53 | Each partner has a % **per product** (MF, Pre-IPO, FD, Bonds, Loans, Equity, PMS, AIF, Advisory, GIFT, Insurance), dated. It comes from the Master MIS partner sheets or is set on the Partners screen. A client follows its partner's % unless it has its own (R-54). | SB card MF 80% from Sep → its clients get 80% from Sep | `ShareIT.partnerSheetsSetRateCardsAndOnlyRealDifferencesBecomeClientExceptions`, `MonthLifecycleIT.processingUsesTheRateCardUnlessTheClientHasItsOwnPercent` |
| R-54 | The % that counts: partner pending → 0; else the client's own % (an exception, set by hand or by upload); else the partner's % for the product that day. A partner with no % for the product stops Process. On upload, a client row's % is an exception only if it is neither the partner's MF % nor one of its other product %s; a client's own % already set (e.g. by hand) stays while the row shows that same %. | Referral card MF 0%, others 50%; row says 50% → follows card (0% on MF). Row says 60% for an 80% SB → exception 60% | `SharePlanTest.aRowPercentIsAClientExceptionOnlyWhenItDiffersFromThePartnersRates`, `SharePlanTest.referralRowsRepeatingTheNonMfPercentFollowThePartnersMfRate`, `SharePlanTest.aClientsOwnPercentSetByHandStaysWhileTheRowShowsTheSamePercent`, `ShareIT.aLinkWithoutClientPercentAndWithoutRateCardIsFlagged`, `MonthLifecycleIT.processingUsesTheRateCardUnlessTheClientHasItsOwnPercent` |
| R-55 | A partner marked "under process" in Master MIS is PENDING: linked to its clients but paid nothing; it becomes ACTIVE when no row says "under process" any more (or by hand). | REFERRAL PARTNER (UNDER PROCESS) → 0% until approved | `SharePlanTest.aPartnerMarkedUnderProcessIsPending`, `ShareIT.partnerSheetsSetRateCardsAndOnlyRealDifferencesBecomeClientExceptions`, `MonthLifecycleIT.processingUsesTheRateCardUnlessTheClientHasItsOwnPercent` |
| R-56 | Insurance is shared with POSPs, not sub-brokers or referral partners: their Insurance % is 0 and is never read as a "general" % on upload. | Insurance column 0% for every SB / RF | `SharePlanTest.aRowPercentIsAClientExceptionOnlyWhenItDiffersFromThePartnersRates` |
| R-57 | A **branch rule** gives every client of a branch (by its RM's branch on the day) a partner, from a date, set on the Org screen. That partner is paid first, at its rate card; the client's own sub-broker and referral partner then share what is left. Master MIS uploads never end it. | Branch C → partner P at 55%: net 100 → P 55, then a 20% referral gets 9 | `MonthLifecycleIT.aBranchPartnerIsPaidFirstForEveryClientOfTheBranch`, `SplitCalculatorTest.aBranchPartnerListedFirstIsPaidBeforeTheClientsOwnSubBrokerThenTheReferral` |
| R-58 | A dated value ended before its first day never took effect (e.g. a master reloaded from the same date without it): it is removed, audited CANCEL with what it was. | Loaded from 1 Aug, reloaded from 1 Aug without the partner → link removed | `ShareIT.aMasterLoadedAgainFromTheSameDateWithoutAPartnerRemovesThatLink`, `MonthLifecycleIT.aBranchPartnerIsPaidFirstForEveryClientOfTheBranch` |

## The split (money out)

| ID | Rule | Example | Proved by |
|---|---|---|---|
| R-30 | Sub broker takes its % of **net** first; the referral partner its % of **what is left**; Epsilon keeps the rest. | Net 100, SB 50%, RF 20% → 50 / 10 / 40 | `SplitCalculatorTest.subBrokerFirstThenReferralOnWhatIsLeftThenEpsilon` |
| R-31 | Partner amounts are rounded to paise; Epsilon takes the remainder, so every line splits back to exactly its net. | 33.333 → 33.33, Epsilon gets the extra | `SplitCalculatorTest.partnersAreRoundedToPaiseAndEpsilonTakesTheRemainderSoItAlwaysAddsUp` |
| R-32 | No partner → Epsilon keeps everything. | Direct client | `SplitCalculatorTest.noPartnerMeansEpsilonKeepsEverything` |
| R-33 | A line is split with the RM, partners and % in force on the last day of its month; the RM/branch/region are stored with the split (later org changes do not rewrite it). | RM moves in Oct → Aug split keeps old RM | `MonthLifecycleIT.saveProcessLockAndCorrect` |

## Changes over time and month closing

| ID | Rule | Example | Proved by |
|---|---|---|---|
| R-40 | RM and sharing changes are dated. A change from a later date keeps earlier months as they were; a change from the entry's own first day **corrects** it (audited). | 50% from Aug, 70% from Oct → Aug–Sep stay 50% | `ShareIT.aClientsPercentIsChangedFromADateOrCorrectedFromItsFirstDay`, `TimelineTest.historyIsNotRewritten` |
| R-41 | Month status: OPEN → PROCESSED → LOCKED. A dated change reaching a processed month reopens it. | Change from 1-Aug → Aug back to OPEN | `MonthLifecycleIT.saveProcessLockAndCorrect` |
| R-42 | Only FINANCE locks; only ADMIN unlocks, with a reason. No dated change may reach into a locked month. | HO cannot lock | `MonthLifecycleIT.saveProcessLockAndCorrect` |
| R-43 | A corrected file for a locked month changes nothing in it: the difference per client is posted as an adjustment in the next open month, split as the locked month stood. | +10 in Aug (locked) → adjustment in Sep | `MonthLifecycleIT.saveProcessLockAndCorrect` |
| R-44 | Ledger lines and splits are append-only (voided, never changed or deleted). | UPDATE refused by the database | `UploadIT.ledgerLinesCannotBeChangedOrDeleted`, `MonthLifecycleIT.splitsCannotBeChangedOrDeleted` |

## Output and access

| ID | Rule | Example | Proved by |
|---|---|---|---|
| R-50 | The END file is a DRAFT until the month is locked, and is built only from the stored split. | Processed month → DRAFT_END_File… | `MonthLifecycleIT.endFileIsADraftUntilLockedAndMasksPanForHo` |
| R-51 | Full PAN only for FINANCE and ADMIN; everyone else sees ABCDE****F. | HO downloads END → masked PAN | `MonthLifecycleIT.endFileIsADraftUntilLockedAndMasksPanForHo`, `PartnerIT.panIsEncryptedAtRestAndMaskedForAnyoneButFinanceAndAdmin` |
| R-52 | Every change is audited (who, when, before, after); the audit log cannot be changed or deleted. | UPDATE audit_log refused | `MigrationIT.auditLogRejectsUpdateAndDelete`, `UserAdminIT.roleChangeIsAuditedWithBeforeAndAfter` |
