package controllers

import com.google.inject.Inject
import uk.gov.dvla.vehicles.presentation.common
import common.clientsidesession.ClientSideSessionFactory
import common.clientsidesession.CookieImplicits.{RichCookies, RichForm, RichResult}
import common.services.DateService
import common.views.helpers.FormExtensions.formBinding
import models.{CompleteAndConfirmFormModel, CompleteAndConfirmViewModel, NewKeeperDetailsViewModel}
import models.VehicleLookupFormModel
//import models.PrivateKeeperDetailsFormModel.Form.ConsentId
import models.CompleteAndConfirmFormModel.Form.{MileageId, ConsentId}
import org.joda.time.format.ISODateTimeFormat
import play.api.data.{FormError, Form}
import play.api.mvc.{Action, AnyContent, Call, Controller, Request, Result}
import play.api.Logger
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.dvla.vehicles.presentation.common
import uk.gov.dvla.vehicles.presentation.common.model.{VehicleDetailsModel, TraderDetailsModel}
import utils.helpers.Config
import views.html.acquire.complete_and_confirm
import webserviceclients.acquire.{TitleType, TraderDetails, AcquireRequestDto, AcquireResponseDto, KeeperDetails}
import webserviceclients.acquire.{AcquireService}


class CompleteAndConfirm @Inject()(webService: AcquireService)(implicit clientSideSessionFactory: ClientSideSessionFactory,
                                                               dateService: DateService,
                                                               config: Config) extends Controller {

  private[controllers] val form = Form(
    CompleteAndConfirmFormModel.Form.Mapping
  )

  private final val NoNewKeeperCookieMessage = "Did not find a new keeper details cookie in cache. " +
    "Now redirecting to Vehicle Lookup."

  private final val NoCookiesFoundMessage = "Failed to find new keeper details and or vehicle details in cache. " +
    "Now redirecting to vehicle lookup"

  def present = Action { implicit request =>
    val newKeeperDetailsOpt = request.cookies.getModel[NewKeeperDetailsViewModel]
    val vehicleDetailsOpt = request.cookies.getModel[VehicleDetailsModel]
    (newKeeperDetailsOpt, vehicleDetailsOpt) match {
      case (Some(newKeeperDetails), Some(vehicleDetails)) =>
        Ok(complete_and_confirm(CompleteAndConfirmViewModel(form.fill(), vehicleDetails, newKeeperDetails), dateService))
      case _ => redirectToVehicleLookup(NoCookiesFoundMessage)
    }
  }

  // TODO: Add checking for provided values, incorporate this with above
  def submit = Action.async { implicit request =>
    form.bindFromRequest.fold(
      invalidForm => Future.successful {
        val newKeeperDetailsOpt = request.cookies.getModel[NewKeeperDetailsViewModel]
        val vehicleDetailsOpt = request.cookies.getModel[VehicleDetailsModel]
        (newKeeperDetailsOpt, vehicleDetailsOpt) match {
          case (Some(newKeeperDetails), Some(vehicleDetails)) =>
            BadRequest(complete_and_confirm(
              CompleteAndConfirmViewModel(formWithReplacedErrors(invalidForm), vehicleDetails, newKeeperDetails), dateService)
            )
          case _ =>
            Logger.debug("Could not find expected data in cache on dispose submit - now redirecting...")
            Redirect(routes.VehicleLookup.present())
        }
      },
      validForm => {
        request.cookies.getModel[NewKeeperDetailsViewModel] match {
          case Some(_) =>
            acquireAction(webService,
              validForm,
//              request.cookies.getModel[BusinessKeeperDetailsFormModel],
//              request.cookies.getModel[PrivateKeeperDetailsFormModel],
              request.cookies.getModel[NewKeeperDetailsViewModel].get,
              request.cookies.trackingId())
          case _ => Future.successful {Redirect(routes.VehicleLookup.present())}
        }
      }
    )
  }
  private def redirectToVehicleLookup(message: String) = {
    Logger.warn(message)
    Redirect(routes.VehicleLookup.present())
  }

  def back = Action { implicit request =>
    request.cookies.getModel[NewKeeperDetailsViewModel] match {
      case Some(keeperDetails) =>
        if (keeperDetails.address.uprn.isDefined) Redirect(routes.NewKeeperChooseYourAddress.present())
        else Redirect(routes.NewKeeperEnterAddressManually.present())
      case None => Redirect(routes.VehicleLookup.present())
    }
  }

  private def formWithReplacedErrors(form: Form[CompleteAndConfirmFormModel]) = {
    form.replaceError(
      ConsentId,
      "error.required",
      FormError(key = ConsentId, message = "acquire_keeperdetailscomplete.consentError", args = Seq.empty)
    ).replaceError(
        MileageId,
        "error.number",
        FormError(key = MileageId, message = "acquire_privatekeeperdetailscomplete.mileage.validation", args = Seq.empty)
      ).distinctErrors
  }

  private def acquireAction(webService: AcquireService,
                            completeAndConfirmFormModel: CompleteAndConfirmFormModel,
//                            businessKeeperDetailsFormModel: Option[BusinessKeeperDetailsFormModel],
//                            privateKeeperDetailsFormModel: Option[PrivateKeeperDetailsFormModel],
                            newKeeperDetailsViewModel: NewKeeperDetailsViewModel,
                            trackingId: String)
                           (implicit request: Request[AnyContent]): Future[Result] = {

    (request.cookies.getModel[TraderDetailsModel], request.cookies.getModel[VehicleLookupFormModel]) match {
      case (Some(traderDetails), Some(vehicleLookup)) =>
        callMicroService(vehicleLookup,
          completeAndConfirmFormModel,
//          businessKeeperDetailsFormModel,
//          privateKeeperDetailsFormModel,
          newKeeperDetailsViewModel,
          traderDetails,
          trackingId)
      case _ =>
        Logger.error("Could not find either dealer details or VehicleLookupFormModel in cache on Acquire submit")
        Future(Redirect(routes.SetUpTradeDetails.present()))
    }
  }

  def nextPage(httpResponseCode: Int, response: Option[AcquireResponseDto]) =
    response match {
      case Some(r) if r.responseCode.isDefined => handleResponseCode(r.responseCode.get)
      case _ => handleHttpStatusCode(httpResponseCode)
    }

  def callMicroService(vehicleLookup: VehicleLookupFormModel,
                       completeAndConfirmForm: CompleteAndConfirmFormModel,
//                       businessKeeperDetailsFormModel: Option[BusinessKeeperDetailsFormModel],
//                       privateKeeperDetailsFormModel: Option[PrivateKeeperDetailsFormModel],
                       newKeeperDetailsViewModel: NewKeeperDetailsViewModel,
                       traderDetails: TraderDetailsModel,
                       trackingId: String)(implicit request: Request[AnyContent]): Future[Result] = {

    val disposeRequest = buildMicroServiceRequest(vehicleLookup, completeAndConfirmForm,
                                                  newKeeperDetailsViewModel, traderDetails)
    webService.invoke(disposeRequest, trackingId).map {
      case (httpResponseCode, response) => {
        Some(Redirect(nextPage(httpResponseCode, response))).
          map(_.withCookie(completeAndConfirmForm)).
          get
      }
    }.recover {
      case e: Throwable =>
        Logger.warn(s"Dispose micro-service call failed.", e)
        Redirect(routes.MicroServiceError.present())
    }
  }

  def buildMicroServiceRequest(vehicleLookup: VehicleLookupFormModel,
                               completeAndConfirmFormModel: CompleteAndConfirmFormModel,
//                               businessKeeperDetailsFormModel: Option[BusinessKeeperDetailsFormModel],
//                               privateKeeperDetailsFormModel: Option[PrivateKeeperDetailsFormModel],
                               newKeeperDetailsViewModel: NewKeeperDetailsViewModel,
                               traderDetailsModel: TraderDetailsModel): AcquireRequestDto = {

//    val keeperDetails = buildKeeperDetails(businessKeeperDetailsFormModel,
//      privateKeeperDetailsFormModel,
//      newKeeperDetailsViewModel,
//      buildTitle(privateKeeperDetailsFormModel))

    val keeperDetails = buildKeeperDetails(newKeeperDetailsViewModel)

    val traderAddress = traderDetailsModel.traderAddress.address
    val traderDetails = TraderDetails(traderOrganisationName = traderDetailsModel.traderName,
      traderAddressLines = getAddressLines(traderAddress, 4),
      traderPostTown = getPostTownFromAddress(traderAddress).getOrElse(""),
      traderPostCode = getPostCodeFromAddress(traderAddress).getOrElse(""),
      traderEmailAddress = traderDetailsModel.traderEmail)

    val dateTimeFormatter = ISODateTimeFormat.dateTime()

    AcquireRequestDto(referenceNumber = vehicleLookup.referenceNumber,
      registrationNumber = vehicleLookup.registrationNumber,
      keeperDetails,
      traderDetails,
      fleetNumber = newKeeperDetailsViewModel.fleetNumber,
      dateOfTransfer = dateTimeFormatter.print(completeAndConfirmFormModel.dateOfSale.toDateTimeAtStartOfDay),
      mileage = completeAndConfirmFormModel.mileage,
      keeperConsent = consentToBoolean(completeAndConfirmFormModel.consent),
      transactionTimestamp = dateTimeFormatter.print(dateService.now.toDateTime)
    )

  }

  private def buildTitle (newKeeperDetailsViewModel: Option[NewKeeperDetailsViewModel]): TitleType = {
    newKeeperDetailsViewModel.title match {
      case Some(tile) => TitleType(titleType = Some(title.titleType), other = Some(title.other))
      case None => TitleType(None, None)
    }
  }

//  def buildKeeperDetails(businessKeeperDetailsFormModel: Option[BusinessKeeperDetailsFormModel],
//                         privateKeeperDetailsFormModel: Option[PrivateKeeperDetailsFormModel],
//                         newKeeperDetailsViewModel: NewKeeperDetailsViewModel,
//                         titleType: TitleType) :KeeperDetails = {
//    (businessKeeperDetailsFormModel, privateKeeperDetailsFormModel) match {
//      case (Some(keeperDetailsModel), None) => buildBusinessKeeperDetails(keeperDetailsModel, newKeeperDetailsViewModel, titleType)
//      case (None, Some(keeperDetailsModel)) => buildPrivateKeeperDetails(keeperDetailsModel, newKeeperDetailsViewModel, titleType)
//    }
//  }

  def buildKeeperDetails(newKeeperDetailsViewModel: NewKeeperDetailsViewModel) :KeeperDetails = {
    val keeperAddress = newKeeperDetailsViewModel.address.address

    KeeperDetails(keeperTitle = buildTitle(newKeeperDetailsViewModel),
      KeeperBusinessName = newKeeperDetailsViewModel.businessName,
      keeperForename = newKeeperDetailsViewModel.firstname,
      keeperSurname = newKeeperDetailsViewModel.surname,
      keeperDateOfBirth = newKeeperDetailsViewModel.dateOfBirth,
      keeperAddressLines = getAddressLines(keeperAddress, 4),
      keeperPostTown = getPostTownFromAddress(keeperAddress).getOrElse(""),
      keeperPostCode = getPostCodeFromAddress(keeperAddress).getOrElse(""),
      keeperEmailAddress = newKeeperDetailsViewModel.email,
      keeperDriverNumber = newKeeperDetailsViewModel.driverNumber)
  }

//  def buildBusinessKeeperDetails(businessKeeperDetailsFormModel: BusinessKeeperDetailsFormModel,
//                                 newKeeperDetailsViewModel: NewKeeperDetailsViewModel,
//                                 titleType: TitleType) :KeeperDetails = {
//
//    val keeperAddress = newKeeperDetailsViewModel.address.address
//
//    KeeperDetails(keeperTitle = titleType,
//      KeeperBusinessName = Option(businessKeeperDetailsFormModel.businessName),
//      keeperForename = None,
//      keeperSurname = None,
//      keeperDateOfBirth = None,
//      keeperAddressLines = getAddressLines(keeperAddress, 4),
//      keeperPostTown = getPostTownFromAddress(keeperAddress).getOrElse(""),
//      keeperPostCode = getPostCodeFromAddress(keeperAddress).getOrElse(""),
//      keeperEmailAddress = businessKeeperDetailsFormModel.email,
//      keeperDriverNumber = None)
//  }

//  def buildPrivateKeeperDetails(privateKeeperDetailsFormModel: PrivateKeeperDetailsFormModel,
//                                newKeeperDetailsViewModel: NewKeeperDetailsViewModel,
//                                titleType: TitleType) :KeeperDetails = {
//
//    val dateTimeFormatter = ISODateTimeFormat.dateTime()
//    val keeperAddress = newKeeperDetailsViewModel.address.address
//
//    KeeperDetails(keeperTitle = titleType,
//      KeeperBusinessName = None,
//      keeperForename = Some(privateKeeperDetailsFormModel.firstName),
//      keeperSurname = Some(privateKeeperDetailsFormModel.lastName),
//      keeperDateOfBirth = Some(dateTimeFormatter.print(privateKeeperDetailsFormModel.dateOfBirth.get.toDateTimeAtStartOfDay)),
//      keeperAddressLines = getAddressLines(keeperAddress, 4),
//      keeperPostTown = getPostTownFromAddress(keeperAddress).getOrElse(""),
//      keeperPostCode = getPostCodeFromAddress(keeperAddress).getOrElse(""),
//      keeperEmailAddress = privateKeeperDetailsFormModel.email,
//      keeperDriverNumber = privateKeeperDetailsFormModel.driverNumber)
//  }

  def handleResponseCode(acquireResponseCode: String): Call =
    acquireResponseCode match {
      case "ms.vehiclesService.response.unableToProcessApplication" =>
        Logger.warn("Acquire soap endpoint redirecting to acquire failure page")
        // TODO : Redirect to error page
        routes.NotImplemented.present()
      case _ =>
        Logger.warn(s"Acquire micro-service failed so now redirecting to micro service error page. " +
          s"Code returned from ms was $acquireResponseCode")
        routes.MicroServiceError.present()
    }

  def handleHttpStatusCode(statusCode: Int): Call =
    statusCode match {
      case OK =>
        routes.AcquireSuccess.present()
      case _ =>
        routes.MicroServiceError.present()
    }

  def consentToBoolean (consent: String): Boolean = {
    consent match {
      case "true" => true
      case _ => false
    }
  }

  def getPostCodeFromAddress (address: Seq[String]): Option[String] = {
    Option(address.last.replace(" ",""))
  }

  def getPostTownFromAddress (address: Seq[String]): Option[String] = {
    Option(address.takeRight(2).head)
  }

  def getAddressLines(address: Seq[String], lines: Int): Seq[String] = {
    val excludeLines = 2
    val getLines = if (lines <= address.length - excludeLines) lines else address.length - excludeLines
    address.take(getLines)
  }

}
