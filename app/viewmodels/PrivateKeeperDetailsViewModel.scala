package viewmodels

import play.api.libs.json.Json
import play.api.data.Forms._
import uk.gov.dvla.vehicles.presentation.common.clientsidesession.CacheKey
import mappings.DropDown.titleDropDown
import uk.gov.dvla.vehicles.presentation.common.mappings.Email.email
import uk.gov.dvla.vehicles.presentation.common.clientsidesession.CacheKey
import play.api.data.Mapping
import uk.gov.dvla.vehicles.presentation.common.views.helpers.FormExtensions._
import uk.gov.dvla.vehicles.presentation.common.clientsidesession.CacheKey
import play.api.data.validation.Constraint
import play.api.data.validation.Constraints._
import uk.gov.dvla.vehicles.presentation.common.clientsidesession.CacheKey

case class PrivateKeeperDetailsViewModel(title: String, firstName: String, email: Option[String])

object PrivateKeeperDetailsViewModel {
  implicit val JsonFormat = Json.format[PrivateKeeperDetailsViewModel]
  final val PrivateKeeperDetailsCacheKey = "privateKeeperDetails"
  implicit val Key = CacheKey[PrivateKeeperDetailsViewModel](PrivateKeeperDetailsCacheKey)

  object Form {
    final val TitleId = "privatekeeper_title"
    final val EmailId = "privatekeeper_email"
    final val FirstNameId = "privatekeeper_firstname"
    final val FirstNameMinLength = 2
    final val FirstNameMaxLength = 25

    def firstNameMapping: Mapping[String] =
      nonEmptyTextWithTransform(_.trim)(FirstNameMinLength, FirstNameMaxLength) verifying validFirstName

    def validFirstName: Constraint[String] = pattern(
      regex = """^[a-zA-Z0-9\s\-\"\,\.\']{2,}$""".r,
      name = "constraint.validFirstName",
      error = "error.validFirstName")

    val titleOptions = Seq(
      ("firstOption", "Mr"),
      ("secondOption", "Mrs"),
      ("thirdOption", "Miss"),
      ("fourthOption", "Other")
    )

    final val Mapping = mapping(
      TitleId -> titleDropDown(titleOptions),
      FirstNameId -> firstNameMapping,
      EmailId -> optional(email)
    )(PrivateKeeperDetailsViewModel.apply)(PrivateKeeperDetailsViewModel.unapply)
  }
}