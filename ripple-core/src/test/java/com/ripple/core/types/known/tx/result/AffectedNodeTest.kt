package com.ripple.core.types.known.tx.result

import com.ripple.core.coretypes.STObject
import com.ripple.core.fields.Field
import com.ripple.utils.normalizeJSON
import com.ripple.utils.normalizedJSON
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AffectedNodeTest {
    @Test
    fun testAffectedCreatedDirectoryNode() {
        @Language("JSON")
        val json = """
        {
            "CreatedNode": {
              "LedgerEntryType": "DirectoryNode",
              "LedgerIndex": "6F86B77ADAC326EA25C597BAD08C447FA568D28A2504883F530520669E693000",
              "NewFields": {
                "ExchangeRate": "530520669E693000",
                "RootIndex": "6F86B77ADAC326EA25C597BAD08C447FA568D28A2504883F530520669E693000",
                "TakerGetsCurrency": "0000000000000000000000004C54430000000000",
                "TakerGetsIssuer": "92D705968936C419CE614BF264B5EEB1CEA47FF4",
                "TakerPaysCurrency": "0000000000000000000000004254430000000000",
                "TakerPaysIssuer": "92D705968936C419CE614BF264B5EEB1CEA47FF4"
              }
            }
        }
        """
        val affected = STObject.fromJSON(json) as AffectedNode
        assertTrue(affected.isDirectoryNode)
        assertTrue(affected.isCreatedNode)

        // This operation doesn't actually make sense, but it's convenient to
        // be able to perform and reduce the nodes to before and after. Though
        // you could of course argue that it makes more sense to return null?

        val asPrevious= affected.nodeAsPrevious().normalizedJSON()
        val asFinal = affected.nodeAsFinal().normalizedJSON()
        val expectedFinal = """{
            "TakerPaysCurrency": "0000000000000000000000004254430000000000",
            "ExchangeRate": "530520669E693000",
            "TakerGetsCurrency": "0000000000000000000000004C54430000000000",
            "TakerGetsIssuer": "92D705968936C419CE614BF264B5EEB1CEA47FF4",
            "LedgerEntryType": "DirectoryNode",
            "index": "6F86B77ADAC326EA25C597BAD08C447FA568D28A2504883F530520669E693000",
            "RootIndex": "6F86B77ADAC326EA25C597BAD08C447FA568D28A2504883F530520669E693000",
            "TakerPaysIssuer": "92D705968936C419CE614BF264B5EEB1CEA47FF4"
        }"""
        assertTrue(expectedFinal.normalizeJSON() == (asFinal))
        assertTrue(expectedFinal.normalizeJSON() == (asPrevious))
    }

    @Test
    fun testAffectedCreatedOffer() {
        @Language("JSON")
        val json = """
        {
            "CreatedNode": {
              "LedgerEntryType": "Offer",
              "LedgerIndex": "A32C940A8962A0FB6EA8CDF0DD9F4CE629DEF8EA360E099F8C634AAE351E6607",
              "NewFields": {
                "Account": "rMWUykAmNQDaM9poSes8VLDZDDKEbmo7MX",
                "BookDirectory": "6F86B77ADAC326EA25C597BAD08C447FA568D28A2504883F530520669E693000",
                "OwnerNode": "000000000000405C",
                "Sequence": 1771489,
                "TakerGets": {
                  "currency": "LTC",
                  "issuer": "rNPRNzBB92BVpAhhZr4iXDTveCgV5Pofm9",
                  "value": "15.92"
                },
                "TakerPays": {
                  "currency": "BTC",
                  "issuer": "rNPRNzBB92BVpAhhZr4iXDTveCgV5Pofm9",
                  "value": "0.2297256"
                }
              }
            }
          }
        """
        val affected = STObject.fromJSON(json) as AffectedNode
        assertTrue(affected.isOffer)
        assertTrue(affected.isCreatedNode)
        val asFinal = affected.nodeAsFinal().normalizedJSON()
        val asPrevious= affected.nodeAsPrevious().normalizedJSON()

        @Language("JSON")
        val expectedFinal = """
            {
              "TakerPays": {
                "currency": "BTC",
                "value": "0.2297256",
                "issuer": "rNPRNzBB92BVpAhhZr4iXDTveCgV5Pofm9"
              },
              "Account": "rMWUykAmNQDaM9poSes8VLDZDDKEbmo7MX",
              "BookDirectory": "6F86B77ADAC326EA25C597BAD08C447FA568D28A2504883F530520669E693000",
              "LedgerEntryType": "Offer",
              "OwnerNode": "000000000000405C",
              "index": "A32C940A8962A0FB6EA8CDF0DD9F4CE629DEF8EA360E099F8C634AAE351E6607",
              "TakerGets": {
                "currency": "LTC",
                "value": "15.92",
                "issuer": "rNPRNzBB92BVpAhhZr4iXDTveCgV5Pofm9"
              },
              "Sequence": 1771489
            }
        """

        assertTrue(expectedFinal.normalizeJSON() == (asFinal))
        assertTrue(expectedFinal.normalizeJSON() == (asPrevious))
    }

    @Test
    fun testAffectedModifiedOffer() {
        @Language("JSON")
        val json = """
          {
            "ModifiedNode": {
              "FinalFields": {
                "Account": "rM3X3QSr8icjTGpaF52dozhbT2BZSXJQYM",
                "BookDirectory": "CF8D13399C6ED20BA82740CFA78E928DC8D498255249BA634C0CA0F87583B9DD",
                "BookNode": "0000000000000000",
                "Flags": 0,
                "OwnerNode": "0000000000000E3F",
                "Sequence": 350199,
                "TakerGets": "5243603512",
                "TakerPays": {
                  "currency": "USD",
                  "issuer": "rMwjYedjc7qqtKYVLiAccJSmCwih4LnE2q",
                  "value": "18.63937817460808"
                }
              },
              "LedgerEntryType": "Offer",
              "LedgerIndex": "2CF16DEFEF0E699E59C5FB37A4698C95E7EE78158C4F8A4FD6EF5C92F678F036",
              "PreviousFields": {
                "TakerGets": "5286156704",
                "TakerPays": {
                  "currency": "USD",
                  "issuer": "rMwjYedjc7qqtKYVLiAccJSmCwih4LnE2q",
                  "value": "18.79064152554785"
                }
              },
              "PreviousTxnID": "2564A4F4F1BE20AA13394FB06DA620814190A090DCDC03320CCB257F00D80B92",
              "PreviousTxnLgrSeq": 7501326
            }
          }
        """
        val affected = STObject.fromJSON(json) as AffectedNode
        assertTrue(affected.isOffer)
        assertTrue(affected.isModifiedNode)
        assertEquals(Field.ModifiedNode, affected.field)
        val asFinal = affected.nodeAsFinal().normalizedJSON()
        val asPrevious= affected.nodeAsPrevious().normalizedJSON()
        @Language("JSON")
        val expectedFinal = """
            {
              "TakerPays": {
                "currency": "USD",
                "value": "18.63937817460808",
                "issuer": "rMwjYedjc7qqtKYVLiAccJSmCwih4LnE2q"
              },
              "Account": "rM3X3QSr8icjTGpaF52dozhbT2BZSXJQYM",
              "PreviousTxnLgrSeq": 7501326,
              "BookDirectory": "CF8D13399C6ED20BA82740CFA78E928DC8D498255249BA634C0CA0F87583B9DD",
              "LedgerEntryType": "Offer",
              "OwnerNode": "0000000000000E3F",
              "index": "2CF16DEFEF0E699E59C5FB37A4698C95E7EE78158C4F8A4FD6EF5C92F678F036",
              "PreviousTxnID": "2564A4F4F1BE20AA13394FB06DA620814190A090DCDC03320CCB257F00D80B92",
              "TakerGets": "5243603512",
              "Flags": 0,
              "Sequence": 350199,
              "BookNode": "0000000000000000"
            }
        """

        @Language("JSON")
        val expectedPrevious = """
            {
              "TakerPays": {
                "currency": "USD",
                "value": "18.79064152554785",
                "issuer": "rMwjYedjc7qqtKYVLiAccJSmCwih4LnE2q"
              },
              "Account": "rM3X3QSr8icjTGpaF52dozhbT2BZSXJQYM",
              "PreviousTxnLgrSeq": 7501326,
              "BookDirectory": "CF8D13399C6ED20BA82740CFA78E928DC8D498255249BA634C0CA0F87583B9DD",
              "LedgerEntryType": "Offer",
              "OwnerNode": "0000000000000E3F",
              "index": "2CF16DEFEF0E699E59C5FB37A4698C95E7EE78158C4F8A4FD6EF5C92F678F036",
              "PreviousTxnID": "2564A4F4F1BE20AA13394FB06DA620814190A090DCDC03320CCB257F00D80B92",
              "TakerGets": "5286156704",
              "Flags": 0,
              "Sequence": 350199,
              "BookNode": "0000000000000000"
            }
        """
        assertTrue(expectedFinal.normalizeJSON() == (asFinal))
        assertTrue(expectedPrevious.normalizeJSON() == (asPrevious))
    }
}