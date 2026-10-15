package money.epsilon.brokerage.ingestion.parser;

import java.math.BigDecimal;

/**
 * One client row of an income file, as read. Amounts keep the file's precision (Investwell: 4 decimals);
 * blanks are zero. {@code rawRm} / {@code rawPartner} are the file's spellings, resolved later.
 */
public record StagedIncomeRow(int rowNumber, String clientCode, String clientCode2, String clientName, String pan,
                              String familyHeadCode, String rawRm, String rawPartner,
                              BigDecimal gross, BigDecimal gst, BigDecimal net) {
}
