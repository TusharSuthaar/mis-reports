package money.epsilon.brokerage.ingestion.parser;

/**
 * Reads one provider's income file into common {@link StagedIncomeRow}s. Investwell now; CAMS / KFin / insurers
 * later, behind the same interface.
 */
public interface IncomeFileParser {

    /** e.g. INVESTWELL — also the source name used for client codes and aliases. */
    String source();

    /** Never throws for bad data: problems are reported in the result. */
    ParsedIncomeFile parse(byte[] content);
}
