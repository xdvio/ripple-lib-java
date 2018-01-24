package com.ripple.core.fields;

import com.ripple.core.formats.Format;
import com.ripple.core.formats.LEFormat;
import com.ripple.core.formats.TxFormat;
import com.ripple.core.serialized.enums.EngineResult;
import com.ripple.core.serialized.enums.LedgerEntryType;
import com.ripple.core.serialized.enums.TransactionType;
import com.ripple.core.types.known.sle.LedgerEntry;
import com.ripple.core.types.known.sle.LedgerHashes;
import com.ripple.core.types.known.sle.entries.*;
import com.ripple.core.types.known.tx.txns.EscrowCreate;
import com.ripple.core.types.known.tx.txns.EscrowCancel;
import com.ripple.core.types.known.tx.txns.SetRegularKey;
import com.ripple.core.types.known.tx.Transaction;
import com.ripple.core.types.known.tx.txns.*;
import com.ripple.core.types.known.tx.txns.pseudo.EnableAmendment;
import com.ripple.core.types.known.tx.txns.pseudo.SetFee;
import com.ripple.utils.TestHelpers;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Test;

import java.io.FileReader;
import java.lang.reflect.Method;
import java.util.*;

import static org.junit.Assert.*;

public class FieldSymbolicsTest {

    private static TreeMap<TransactionType, Class<? extends Transaction>> txns =
            new TreeMap<>();

    private static TreeMap<LedgerEntryType, Class<? extends LedgerEntry>> les =
            new TreeMap<>();

    static {

        les.put(LedgerEntryType.AccountRoot, AccountRoot.class);
        les.put(LedgerEntryType.DirectoryNode, DirectoryNode.class);
        les.put(LedgerEntryType.RippleState, RippleState.class);
        les.put(LedgerEntryType.Check, Check.class);
        les.put(LedgerEntryType.Escrow, Escrow.class);
        les.put(LedgerEntryType.Offer, Offer.class);
        les.put(LedgerEntryType.LedgerHashes, LedgerHashes.class);
        les.put(LedgerEntryType.Amendments, Amendments.class);
        les.put(LedgerEntryType.FeeSettings, FeeSettings.class);
        les.put(LedgerEntryType.Ticket, Ticket.class);
        les.put(LedgerEntryType.SignerList, SignerList.class);
        les.put(LedgerEntryType.PayChannel, PayChannel.class);

        txns.put(TransactionType.Payment, Payment.class);
        txns.put(TransactionType.CheckCreate, CheckCreate.class);
        txns.put(TransactionType.CheckCash, CheckCash.class);
        txns.put(TransactionType.CheckCancel, CheckCancel.class);
        txns.put(TransactionType.AccountSet, AccountSet.class);
        txns.put(TransactionType.SetRegularKey, SetRegularKey.class);
        txns.put(TransactionType.TrustSet, TrustSet.class);
        txns.put(TransactionType.OfferCancel, OfferCancel.class);
        txns.put(TransactionType.OfferCreate, OfferCreate.class);
        txns.put(TransactionType.TicketCancel, TicketCancel.class);
        txns.put(TransactionType.TicketCreate, TicketCreate.class);
        txns.put(TransactionType.EscrowCancel, EscrowCancel.class);
        txns.put(TransactionType.EscrowCreate, EscrowCreate.class);
        txns.put(TransactionType.EscrowFinish, EscrowFinish.class);
        txns.put(TransactionType.SignerListSet, SignerListSet.class);
        txns.put(TransactionType.PaymentChannelCreate, PaymentChannelCreate.class);
        txns.put(TransactionType.PaymentChannelFund, PaymentChannelFund.class);
        txns.put(TransactionType.PaymentChannelClaim, PaymentChannelClaim.class);
        txns.put(TransactionType.EnableAmendment, EnableAmendment.class);
        txns.put(TransactionType.SetFee, SetFee.class);

        for (TransactionType tt : txns.keySet()) {
            assertEquals(txns.get(tt).getSimpleName(), tt.name());
        }
        for (LedgerEntryType let : les.keySet()) {
            assertEquals(les.get(let).getSimpleName(), let.name());
        }
    }

    @Test
    public void CheckProtocolDefinitions() {
        FileReader reader = TestHelpers.getResourceReader("protocol.json");
        JSONObject o = new JSONObject(new JSONTokener(reader));

        checkFields(o.getJSONArray("fields"));
        checkTransactionTypes(o.getJSONArray("transactions"));
        checkLedgerEntries(o.getJSONArray("ledgerEntries"));
        checkEngineResults(o.getJSONObject("engineResults"));
    }

    private void checkEngineResults(JSONObject engineResults) {
        Iterator keys = engineResults.keys();
        Map<String, EngineResult> results = new HashMap<String, EngineResult>();
        for (EngineResult r : EngineResult.values()) {
            results.put(r.name(), r);
            assertTrue("No old codes", engineResults.has(r.name()));
        }
//        TreeMap<Integer, String> sorted = new TreeMap<Integer, String>();
        while (keys.hasNext()) {
            String key = (String) keys.next();
            JSONObject resultObj = engineResults.getJSONObject(key);
            int ordinal = resultObj.getInt("ordinal");
            String description = resultObj.getString("description");
            String decl = makeDeclarationLine(key, ordinal, description);
            assertTrue("missing " + decl, results.containsKey(key));
            EngineResult ter = results.get(key);
            assertEquals(decl, ter.asInteger(), ordinal);
            assertEquals(decl, ter.human, description);
//            sorted.put(ordinal, decl);
        }
//        for (String s : sorted.values()) {
//            System.out.println(s);
//        }
    }

    private String makeDeclarationLine(String key, int ordinal, String description) {
        if (ordinal >= 0 || Math.abs(ordinal) % 100 == 99) {
            return String.format(Locale.US, "%s(%d, \"%s\"),",
                    key, ordinal,
                    description);
        } else {
            return String.format(Locale.US, "%s(\"%s\"),", key, description);
        }
    }

    private void checkTransactionTypes(JSONArray txns) {
        for (int i = 0; i < txns.length(); i++) {
            JSONObject tx = txns.getJSONObject(i);
            String txName = tx.getString("name");
            int ordinal = tx.getInt("ordinal");

            try {
                java.lang.reflect.Field f = TxFormat.class.getField(txName);
                assertTrue(java.lang.reflect.Modifier.isStatic(f.getModifiers()));
            } catch (Exception e) {
                throw new IllegalStateException(
                        "TxFormat is missing named declaration: "
                                + txName);
            }

            if (!txName.isEmpty()) {
                try {
                    TransactionType txType = TransactionType.valueOf(txName);
                    if (txType.asInteger() != ordinal) {
                        fail("incorrect ordinal for " +
                                txType + ", expected=" +
                                ordinal + " actual=" +
                                txType.asInteger());
                    }

                } catch (IllegalArgumentException e) {
                    fail("missing TransactionType " +
                            txName);
                }
                Format txFormat = TxFormat.fromString(txName);
                assertNotNull(txFormat);
                checkFormat(tx, txFormat);

                @SuppressWarnings("unchecked") EnumMap<Field, Format.Requirement>
                        requirements = txFormat.requirements();

                TransactionType key = TransactionType.valueOf(txName);
                assertTrue("FieldSymbolicsTest.txns missing " + key, FieldSymbolicsTest.txns.containsKey(key));
                Class<?> kls = FieldSymbolicsTest.txns.get(key);

                checkMethods(txFormat, requirements, kls);
            }


            assertTrue(
                    txName,
                    FieldSymbolicsTest.txns.containsKey(TransactionType.valueOf(txName)));
        }

        assertEquals(txns.length(),
                TransactionType.values().length);

    }

    private void checkMethods(Format txFormat, EnumMap<Field, Format.Requirement> requirements, Class<?> kls) {
         System.out.println("Methods for: " + kls.getSimpleName());
        boolean missing = false;
        for (Field field : requirements.keySet()) {
            Format.Requirement requirement = requirements.get(field);
            boolean optional = requirement == Format.Requirement.OPTIONAL;
            String fieldType = field.type.name();
            if (optional && !txFormat.isCommon(field)) {
                String name = String.format("has%s", field.name());
                Method method = getMethod(kls, name, 0);
                if (method == null) {
                    String body =
                            String.format("{return has(%s.%s);}",
                                    fieldType, field.name());
                    String start =
                            String.format("public boolean %s() %s",
                                    name, body);
                    System.out.println(start);
                    missing = true;
                }
            }
            if (!txFormat.isCommon(field)) {
                String methodName = camelize(field.name());
                Method method = getMethod(kls, methodName, 0);
                if (method == null) {
                    String body = String.format("{return get(%s.%s);}",
                            fieldType, field.name());
                    String start = String.format(
                            "public %s %s() %s",
                            fieldType,
                            methodName,
                            body);
                    System.out.println(start);
                    missing = true;
                }
                methodName = camelize(field.name());
                method = getMethod(kls, methodName, 1);
                if (method == null) {
                    String body =
                            String.format("{ put(%s.%s, val);}",
                                    fieldType, field.name());
                    String declaration =
                            String.format("public void %s(%s val) %s",
                                    methodName,
                                    fieldType, body);
                    System.out.println(declaration);
                    missing = true;
                } else {
                    // System.out.println(method.toGenericString());
                }

            }
            // assertFalse(missing);
        }
    }

    private Method getMethod(Class<?> kls, String name, int paramCount) {
        for (Method method : kls.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == paramCount) {
                return method;
            }
        }
        return null;
    }

    private void checkFormat(JSONObject obj, Format<?> format) {
        String txName = obj.getString("name");

        if (format == null) {
            throw new IllegalArgumentException();
        }
        EnumMap<Field, Format.Requirement> requirements = format.requirements();
        JSONArray fields = obj.getJSONArray("fields");

        for (int j = 0; j < fields.length(); j++) {
            JSONArray field = fields.getJSONArray(j);
            String fieldName = field.getString(0);
            String requirement = field.getString(1);

            Field key = Field.fromString(fieldName);
            if (!requirements.containsKey(key)) {
                fail(String.format("%s format missing %s %s %n",
                        txName, requirement, fieldName));
            } else {
                Format.Requirement req = requirements.get(key);
                if (!req.toString().equals(requirement)) {
                    fail(String.format("%s format missing %s %s %n",
                            txName, requirement, fieldName));

                }
            }
        }
        // check length is same, and if none are missing, must be equal ;)
        assertEquals(obj.toString(2),
                fields.length(), requirements.size());

    }

    private String camelize(String name) {
        return name.substring(0, 1).toLowerCase() + name.substring(1);
    }

    private void checkLedgerEntries(JSONArray entries) {
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entryJson = entries.getJSONObject(i);
            String name = entryJson.getString("name");
            int ordinal = entryJson.getInt("ordinal");

            try {
                java.lang.reflect.Field f = LEFormat.class.getField(name);
                assertTrue(java.lang.reflect.Modifier.isStatic(f.getModifiers()));
            } catch (Exception e) {
                throw new IllegalStateException(
                        "LEFormat is missing named declaration: "
                                + name);
            }

            if (!name.isEmpty()) {
                try {
                    LedgerEntryType let = LedgerEntryType.valueOf(name);
                    if (let.asInteger() != ordinal) {
                        fail("incorrect ordinal for " +
                                let + ", expected=" +
                                (char) ordinal + " actual=" +
                                (char) ((int) let.asInteger()));
                    }
                } catch (IllegalArgumentException e) {
                    fail("missing LedgerEntryType for " +
                            entryJson);
                }
                LEFormat format = LEFormat.fromString(name);
                assertNotNull(format);
                checkFormat(entryJson, format);

                LedgerEntryType key = LedgerEntryType.valueOf(name);
                assertTrue(FieldSymbolicsTest.les.containsKey(key));
                Class<?> kls = FieldSymbolicsTest.les.get(key);
                // System.out.println("Methods for: " + txName);
                //noinspection unchecked
                checkMethods(format, format.requirements(), kls);
            }

            if (name.isEmpty()) {
                throw new IllegalStateException("name is empty");
            }

        }
        assertEquals(entries.length(), LedgerEntryType.values().length);
    }

    private void checkFields(JSONArray fields) {
        TreeSet<String> names = new TreeSet<String>();
        for (int i = 0; i < fields.length(); i++) {
            JSONObject fieldJson = fields.getJSONObject(i);
            String nam = fieldJson.getString("name");
            names.add(nam);
            if (!nam.isEmpty()) {
                try {
                    Field f = Field.valueOf(fieldJson.getString("name"));
                    Type t = Type.valueOf(fieldJson.getString("type"));
                    assertEquals(fieldJson.toString(2),
                            f.isSigningField(),
                            fieldJson.getBoolean("isSigningField"));
                    assertEquals(
                            fieldJson.toString(2),
                            f.isSerialized(),
                            fieldJson.getBoolean("isBinary"));
                    assertEquals(fieldJson.toString(2), f.type.id, t.id);
                    assertEquals(fieldJson.toString(2), f.id, fieldJson
                            .getInt("ordinal"));
                } catch (IllegalArgumentException e) {
                    fail("Can't find Field or Type for "
                            + fieldJson);
                }
            }
        }

        for (Field field : Field.values()) {
            // We have extra fields declared here
            if (field.isSerialized() && !names.contains(field.name())) {
                if (!((field == Field.ArrayEndMarker) ||
                        (field == Field.ObjectEndMarker)))
                    fail(field.toString());
            }
        }
    }
}
