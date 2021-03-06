package cz.applifting.humansis.ui.main.distribute.beneficiary

import android.nfc.Tag
import androidx.lifecycle.MutableLiveData
import cz.applifting.humansis.misc.NfcTagPublisher
import cz.applifting.humansis.model.db.BeneficiaryLocal
import cz.applifting.humansis.repositories.BeneficiariesRepository
import cz.applifting.humansis.ui.BaseViewModel
import cz.applifting.humansis.ui.main.distribute.beneficiary.BeneficiaryDialog.Companion.ALREADY_ASSIGNED
import cz.applifting.humansis.ui.main.distribute.beneficiary.BeneficiaryDialog.Companion.INVALID_CODE
import cz.quanti.android.nfc.OfflineFacade
import cz.quanti.android.nfc_io_libray.types.NfcUtil
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject
import io.reactivex.Single
import java.util.*

/**
 * Created by Vaclav Legat <vaclav.legat@applifting.cz>
 * @since 9. 9. 2019
 */

class BeneficiaryViewModel @Inject constructor(private val beneficiariesRepository: BeneficiariesRepository) :
    BaseViewModel() {

    val beneficiaryLD = MutableLiveData<BeneficiaryLocal>()
    val scannedIdLD = MutableLiveData<String>()
    val scannedCardIdLD = MutableLiveData<String>()
    val goBackEventLD = MutableLiveData<Unit>()
    @Inject
    lateinit var nfcTagPublisher: NfcTagPublisher
    @Inject
    lateinit var nfcFacade: OfflineFacade

    var previousEditState: Boolean? = null
    var isAssignedInOtherDistribution: Boolean = false
    private set

    private val BOOKLET_REGEX = "^\\d{1,6}-\\d{1,6}-\\d{1,6}$".toRegex(RegexOption.IGNORE_CASE)
    private val NEW_BOOKLET_REGEX = "^[a-zA-Z0-9]{2,3}_.+_[0-9]{1,2}-[0-9]{1,2}-[0-9]{2,4}_batch[0-9]+$".toRegex(RegexOption.IGNORE_CASE)

    fun initBeneficiary(id: Int) {
        launch {
            beneficiariesRepository.getBeneficiaryOfflineFlow(id)
                .collect {
                    it?.let {
                        isAssignedInOtherDistribution = beneficiariesRepository.isAssignedInOtherDistribution(it)
                        beneficiaryLD.value = it
                    } ?: run {
                        goBackEventLD.value = Unit
                    }
                }
        }
    }

    fun scanQRBooklet(code: String?) {
        launch {
            val beneficiary = beneficiaryLD.value!!.copy(
                qrBooklets = listOfNotNull(code)
            )

            beneficiariesRepository.updateBeneficiaryOffline(beneficiary)
            beneficiaryLD.value = beneficiary
        }
    }

    fun depositMoneyToCard(value: Double, currency: String, otherCard: String?, isNew: Boolean, pin: String, ownerId: Int): Single<Tag> {
        return if(isNew)
        {
            nfcTagPublisher.getTagObservable().firstOrError().flatMap { tag ->
                nfcFacade.writeProtectedBalanceForUser(tag, pin, value, ownerId.toString(), currency).toSingleDefault(tag)
            }
        } else {
            Single.fromObservable(
                    nfcTagPublisher.getTagObservable().take(1).flatMapSingle { tag ->
                        val id = NfcUtil.toHexString(tag.id).toUpperCase(Locale.US)

                        if(otherCard == null || id != otherCard) {
                            throw CardMismatchException(otherCard)
                        }
                        nfcFacade.increaseBalanceForUser(tag, value, ownerId.toString(), currency).flatMap {
                            Single.just(tag)
                        }
                    })
        }
    }

    fun saveCard(cardId: String?) {
        launch {
            val beneficiary = beneficiaryLD.value!!.copy(
                newSmartcard = cardId?.toUpperCase(Locale.US)
            )

            beneficiariesRepository.updateBeneficiaryOffline(beneficiary)
            beneficiaryLD.value = beneficiary
            scannedCardIdLD.value = cardId
        }
    }

    internal fun revertBeneficiary() {
        launch {
            val beneficiary = beneficiaryLD.value!!
            val updatedBeneficiary = beneficiary.copy(
                distributed = false,
                edited = false,
                qrBooklets = emptyList(),
                referralType = beneficiary.originalReferralType,
                referralNote = beneficiary.originalReferralNote
            )

            beneficiariesRepository.updateBeneficiaryOffline(updatedBeneficiary)
            beneficiaryLD.value = updatedBeneficiary
        }
    }

    internal fun checkScannedId(scannedId: String) {
        launch {
            val assigned = beneficiariesRepository.checkBoookletAssignedLocally(scannedId)

            val bookletId = when {
                assigned -> ALREADY_ASSIGNED
                isValidBookletCode(scannedId) -> scannedId
                else -> INVALID_CODE
            }
            scannedIdLD.value = bookletId
        }
    }

    private fun isValidBookletCode(code: String): Boolean {
        return (BOOKLET_REGEX.matches(code) || NEW_BOOKLET_REGEX.matches(code))
    }

}