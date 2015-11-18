package models

import models.AcquireCacheKeyPrefix.CookiePrefix
import play.api.data.Forms.mapping
import play.api.libs.json.Json
import uk.gov.dvla.vehicles.presentation.common
import common.clientsidesession.CacheKey
import common.mappings.ThreeAlphasConstraint.threeAlphasConstraint
import common.views.models.AddressAndPostcodeViewModel

final case class NewKeeperEnterAddressManuallyFormModel(addressAndPostcodeModel: AddressAndPostcodeViewModel)

object NewKeeperEnterAddressManuallyFormModel {
  implicit val JsonFormat = Json.format[NewKeeperEnterAddressManuallyFormModel]

  final val NewKeeperEnterAddressManuallyCacheKey = s"${CookiePrefix}newKeeperEnterAddressManually"
  implicit val Key = CacheKey[NewKeeperEnterAddressManuallyFormModel](NewKeeperEnterAddressManuallyCacheKey)

  object Form {

    final val AddressAndPostcodeId = "addressAndPostcode"
    final val Mapping = mapping(
    AddressAndPostcodeId -> AddressAndPostcodeViewModel.Form.Mapping.verifying(threeAlphasConstraint)
    )(NewKeeperEnterAddressManuallyFormModel.apply)(NewKeeperEnterAddressManuallyFormModel.unapply)
  }
}
