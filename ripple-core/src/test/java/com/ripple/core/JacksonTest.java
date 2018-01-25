package com.ripple.core;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ripple.core.coretypes.Amount;
import com.ripple.core.coretypes.STObject;
import com.ripple.core.coretypes.hash.Hash256;
import com.ripple.core.types.known.tx.Transaction;
import com.ripple.encodings.json.JSON;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class JacksonTest {

    private static final String amount = "{\n" +
            "  \"currency\" : \"USD\",\n" +
            "  \"issuer\": \"rvYAfWj5gh67oV6fW32ZzP3Aw4Eubs59B\",\n" +
            "  \"value\" : \"1000.1\"\n" +
            "}";

    private static final String transactionMetaHex = "201C00000000F8E311006F563596CE72C902BAFAAB56CC486ACAF9B4AFC67CF7CADBB81A4AA9CBDC8C5CB1AAE824000195F934000000000000000E501062A3338CAF2E1BEE510FC33DE1863C56948E962CCE173CA55C14BE8A20D7F00064400000170A53AC2065D5460561EC9DE000000000000000000000000000494C53000000000092D705968936C419CE614BF264B5EEB1CEA47FF4811439408A69F0895E62149CFCC006FB89FA7D1E6E5DE1E1E31100645662A3338CAF2E1BEE510FC33DE1863C56948E962CCE173CA55C14BE8A20D7F000E8365C14BE8A20D7F0005862A3338CAF2E1BEE510FC33DE1863C56948E962CCE173CA55C14BE8A20D7F0000311000000000000000000000000494C530000000000041192D705968936C419CE614BF264B5EEB1CEA47FF4E1E1E511006456AB03F8AA02FFA4635E7CE2850416AEC5542910A2B4DBE93C318FEB08375E0DB5E7220000000032000000000000000058801C5AFB5862D4666D0DF8E5BE1385DC9B421ED09A4269542A07BC0267584B64821439408A69F0895E62149CFCC006FB89FA7D1E6E5DE1E1E511006125003136FA55DE15F43F4A73C4F6CB1C334D9E47BDE84467C0902796BB81D4924885D1C11E6D56CF23A37E39A571A0F22EC3E97EB0169936B520C3088963F16C5EE4AC59130B1BE624000195F92D000000086240000018E16CCA08E1E7220000000024000195FA2D000000096240000018E16CC9FE811439408A69F0895E62149CFCC006FB89FA7D1E6E5DE1E1F1031000";
    private static final String transaction = "{" +
            "  \"Account\": \"raD5qJMAShLeHZXf9wjUmo6vRK4arj9cF3\"," +
            "  \"Fee\": \"10\"," +
            "  \"Flags\": 0," +
            "  \"Sequence\": 103929," +
            "  \"SigningPubKey\": \"028472865AF4CB32AA285834B57576B7290AA8C31B459047DB27E16F418D6A7166\"," +
            "  \"TakerGets\": {" +
            "    \"currency\": \"ILS\"," +
            "    \"issuer\": \"rNPRNzBB92BVpAhhZr4iXDTveCgV5Pofm9\"," +
            "    \"value\": \"1694.768\"" +
            "  }," +
            "  \"TakerPays\": \"98957503520\"," +
            "  \"TransactionType\": \"OfferCreate\"," +
            "  \"TxnSignature\": \"304502202ABE08D5E78D1E74A4C18F2714F64E87B8BD57444AFA5733109EB3C077077520022100DB335EE97386E4C0591CAC024D50E9230D8F171EEB901B5E5E4BD6D1E0AEF98C\"," +
            "  \"hash\": \"232E91912789EA1419679A4AA920C22CFC7C6B601751D6CBE89898C26D7F4394\"," +
            "  \"metaData\": {" +
            "    \"AffectedNodes\": [" +
            "      {" +
            "        \"CreatedNode\": {" +
            "          \"LedgerEntryType\": \"Offer\"," +
            "          \"LedgerIndex\": \"3596CE72C902BAFAAB56CC486ACAF9B4AFC67CF7CADBB81A4AA9CBDC8C5CB1AA\"," +
            "          \"NewFields\": {" +
            "            \"Account\": \"raD5qJMAShLeHZXf9wjUmo6vRK4arj9cF3\"," +
            "            \"BookDirectory\": \"62A3338CAF2E1BEE510FC33DE1863C56948E962CCE173CA55C14BE8A20D7F000\"," +
            "            \"OwnerNode\": \"000000000000000E\"," +
            "            \"Sequence\": 103929," +
            "            \"TakerGets\": {" +
            "              \"currency\": \"ILS\"," +
            "              \"issuer\": \"rNPRNzBB92BVpAhhZr4iXDTveCgV5Pofm9\"," +
            "              \"value\": \"1694.768\"" +
            "            }," +
            "            \"TakerPays\": \"98957503520\"" +
            "          }" +
            "        }" +
            "      }," +
            "      {" +
            "        \"CreatedNode\": {" +
            "          \"LedgerEntryType\": \"DirectoryNode\"," +
            "          \"LedgerIndex\": \"62A3338CAF2E1BEE510FC33DE1863C56948E962CCE173CA55C14BE8A20D7F000\"," +
            "          \"NewFields\": {" +
            "            \"ExchangeRate\": \"5C14BE8A20D7F000\"," +
            "            \"RootIndex\": \"62A3338CAF2E1BEE510FC33DE1863C56948E962CCE173CA55C14BE8A20D7F000\"," +
            "            \"TakerGetsCurrency\": \"000000000000000000000000494C530000000000\"," +
            "            \"TakerGetsIssuer\": \"92D705968936C419CE614BF264B5EEB1CEA47FF4\"" +
            "          }" +
            "        }" +
            "      }," +
            "      {" +
            "        \"ModifiedNode\": {" +
            "          \"FinalFields\": {" +
            "            \"Flags\": 0," +
            "            \"IndexPrevious\": \"0000000000000000\"," +
            "            \"Owner\": \"raD5qJMAShLeHZXf9wjUmo6vRK4arj9cF3\"," +
            "            \"RootIndex\": \"801C5AFB5862D4666D0DF8E5BE1385DC9B421ED09A4269542A07BC0267584B64\"" +
            "          }," +
            "          \"LedgerEntryType\": \"DirectoryNode\"," +
            "          \"LedgerIndex\": \"AB03F8AA02FFA4635E7CE2850416AEC5542910A2B4DBE93C318FEB08375E0DB5\"" +
            "        }" +
            "      }," +
            "      {" +
            "        \"ModifiedNode\": {" +
            "          \"FinalFields\": {" +
            "            \"Account\": \"raD5qJMAShLeHZXf9wjUmo6vRK4arj9cF3\"," +
            "            \"Balance\": \"106861218302\"," +
            "            \"Flags\": 0," +
            "            \"OwnerCount\": 9," +
            "            \"Sequence\": 103930" +
            "          }," +
            "          \"LedgerEntryType\": \"AccountRoot\"," +
            "          \"LedgerIndex\": \"CF23A37E39A571A0F22EC3E97EB0169936B520C3088963F16C5EE4AC59130B1B\"," +
            "          \"PreviousFields\": {" +
            "            \"Balance\": \"106861218312\"," +
            "            \"OwnerCount\": 8," +
            "            \"Sequence\": 103929" +
            "          }," +
            "          \"PreviousTxnID\": \"DE15F43F4A73C4F6CB1C334D9E47BDE84467C0902796BB81D4924885D1C11E6D\"," +
            "          \"PreviousTxnLgrSeq\": 3225338" +
            "        }" +
            "      }" +
            "    ]," +
            "    \"TransactionIndex\": 0," +
            "    \"TransactionResult\": \"tesSUCCESS\"" +
            "  }" +
            "}";

    @Test
    public void testAmount() {
        ObjectNode parse = JSON.parseObject(amount);
        Amount amount = Amount.fromJacksonObject(parse);
        assertEquals("1000.1/USD/rvYAfWj5gh67oV6fW32ZzP3Aw4Eubs59B",
                amount.toTextFull());
    }

    @Test
    public void testTransaction() {
        ObjectNode parse = JSON.parseObject(transaction);
        String hash = parse.remove("hash").asText();
        STObject fields = STObject.fromJacksonObject(parse);
        Transaction tx = (Transaction) fields;
        assertEquals(hash, tx.createHash().toHex());
    }

    @Test
    public void testTransactionMeta() {
        ObjectNode parse = JSON.parseObject(transaction);
        ObjectNode meta = (ObjectNode) parse.remove("metaData");
        STObject fields = STObject.fromJacksonObject(meta);
        assertEquals(transactionMetaHex, fields.toHex());
    }
}
