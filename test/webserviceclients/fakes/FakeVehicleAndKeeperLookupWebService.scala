package webserviceclients.fakes

import org.joda.time.DateTime
import play.api.http.Status.{OK, SERVICE_UNAVAILABLE}
import play.api.libs.json.Json
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.dvla.vehicles.presentation.common.webserviceclients.vehicleandkeeperlookup.VehicleAndKeeperDetailsDto
import uk.gov.dvla.vehicles.presentation.common.webserviceclients.vehicleandkeeperlookup.VehicleAndKeeperDetailsRequest
import uk.gov.dvla.vehicles.presentation.common.webserviceclients.vehicleandkeeperlookup.VehicleAndKeeperLookupErrorMessage
import uk.gov.dvla.vehicles.presentation.common.webserviceclients.vehicleandkeeperlookup.VehicleAndKeeperLookupResponse
import uk.gov.dvla.vehicles.presentation.common.webserviceclients.vehicleandkeeperlookup.VehicleAndKeeperLookupWebService

final class FakeVehicleAndKeeperLookupWebService extends VehicleAndKeeperLookupWebService {
  import webserviceclients.fakes.FakeVehicleAndKeeperLookupWebService._

  override def invoke(request: VehicleAndKeeperDetailsRequest, trackingId: String) = Future {
    val (responseStatus, response) = {
      request.referenceNumber match {
        case "99999999991" => vehicleDetailsResponseVRMNotFound
        case "99999999992" => vehicleDetailsResponseDocRefNumberNotLatest
        case "99999999993" => vehicleDetailsKeeperStillOnRecordResponseSuccess
        case "99999999999" => vehicleDetailsResponseNotFoundResponseCode
        case _ => vehicleDetailsResponseSuccess
      }
    }
    val responseAsJson = Json.toJson(response)
    new FakeResponse(status = responseStatus, fakeJson = Some(responseAsJson)) // Any call to a webservice will always return this successful response.
  }
}

object FakeVehicleAndKeeperLookupWebService {
  final val SoldToIndividual = ""
  final val RegistrationNumberValid = "AB12AWR"
  final val RegistrationNumberWithSpaceValid = "AB12 AWR"
  final val ReferenceNumberValid = "12345678910"
  final val VehicleMakeValid = "Alfa Romeo"
  final val VehicleModelValid = "Alfasud ti"
  final val KeeperNameValid = "Keeper Name"
  final val KeeperUprnValid = 10123456789L
  final val ConsentValid = "true"
  final val TransactionIdValid = "A1-100"
  final val VrmNotFound = VehicleAndKeeperLookupErrorMessage(code = "", message = "vehicle_lookup_vrm_not_found")
  final val DocumentRecordMismatch = VehicleAndKeeperLookupErrorMessage(code = "", message = "vehicle_lookup_document_record_mismatch")
  final val TransactionTimestampValid = new DateTime()
  final val UnhandledException = "unhandled_exception"

  // TODO : Use proper values here
  private def vehicleDetails(disposeFlag: Boolean = true) =
    VehicleAndKeeperDetailsDto(
      registrationNumber = RegistrationNumberValid,
      vehicleMake = Some(VehicleMakeValid),
      vehicleModel = Some(VehicleModelValid),
      keeperTitle = Some("a"),
      keeperFirstName = Some("a"),
      keeperLastName = Some("a"),
      keeperAddressLine1 = Some("a"),
      keeperAddressLine2 = Some("a"),
      keeperAddressLine3 = Some("a"),
      keeperAddressLine4 = Some("a"),
      keeperPostTown = Some("a"),
      keeperPostcode = Some("a"),
      disposeFlag = Some(disposeFlag),
      keeperEndDate = if (disposeFlag) Some(new DateTime()) else None,
      keeperChangeDate = None,
      suppressedV5Flag = None
    )

  val vehicleDetailsResponseSuccess: (Int, Option[VehicleAndKeeperLookupResponse]) = {
    (OK, Some(VehicleAndKeeperLookupResponse(responseCode = None, Some(vehicleDetails()))))
  }

  val vehicleDetailsKeeperStillOnRecordResponseSuccess: (Int, Option[VehicleAndKeeperLookupResponse]) = {
    (OK, Some(VehicleAndKeeperLookupResponse(responseCode = None, Some(vehicleDetails(disposeFlag = false)))))
  }

  val vehicleDetailsResponseVRMNotFound: (Int, Option[VehicleAndKeeperLookupResponse]) = {
    (OK, Some(VehicleAndKeeperLookupResponse(responseCode = Some(VrmNotFound), None)))
  }

  val vehicleDetailsResponseDocRefNumberNotLatest: (Int, Option[VehicleAndKeeperLookupResponse]) = {
    (OK, Some(VehicleAndKeeperLookupResponse(responseCode = Some(DocumentRecordMismatch), None)))
  }

  val vehicleDetailsResponseNotFoundResponseCode: (Int, Option[VehicleAndKeeperLookupResponse]) = {
    (OK, Some(VehicleAndKeeperLookupResponse(responseCode = None, None)))
  }

  val vehicleDetailsResponseUnhandledException: (Int, Option[VehicleAndKeeperLookupResponse]) = {
    (OK, Some(VehicleAndKeeperLookupResponse(responseCode = Some(DocumentRecordMismatch), None)))
  }

  val vehicleDetailsServerDown: (Int, Option[VehicleAndKeeperLookupResponse]) = {
    (SERVICE_UNAVAILABLE, None)
  }

  val vehicleDetailsNoResponse: (Int, Option[VehicleAndKeeperLookupResponse]) = {
    (OK, None)
  }
}