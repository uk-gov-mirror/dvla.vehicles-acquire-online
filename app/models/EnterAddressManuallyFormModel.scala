package models

import play.api.data.Forms.mapping
import play.api.libs.json.Json
import uk.gov.dvla.vehicles.presentation.common.clientsidesession.CacheKey
import uk.gov.dvla.vehicles.presentation.common.views.models.AddressAndPostcodeViewModel
import models.AcquireCacheKeyPrefix.CookiePrefix
import mappings.ThreeAlphasConstraint.threeAlphasConstraint

final case class EnterAddressManuallyFormModel(addressAndPostcodeModel: AddressAndPostcodeViewModel)

object EnterAddressManuallyFormModel {
  implicit val JsonFormat = Json.format[EnterAddressManuallyFormModel]

  final val EnterAddressManuallyCacheKey = s"${CookiePrefix}enterAddressManually"
  implicit val Key = CacheKey[EnterAddressManuallyFormModel](EnterAddressManuallyCacheKey)

  object Form {
    final val AddressAndPostcodeId = "addressAndPostcode"
    final val Mapping = mapping(
      AddressAndPostcodeId -> AddressAndPostcodeViewModel.Form.Mapping.verifying(threeAlphasConstraint)
    )(EnterAddressManuallyFormModel.apply)(EnterAddressManuallyFormModel.unapply)
  }
}
