package controllers

import javax.inject.Inject
import play.api.Logger
import play.api.data.{Form, FormError}
import play.api.i18n.Lang
import play.api.mvc.{AnyContent, Action, Controller, Request, Result}
import models.BusinessChooseYourAddressFormModel.Form.AddressSelectId
import models.NewKeeperChooseYourAddressViewModel
import models.BusinessKeeperDetailsFormModel
import models.NewKeeperChooseYourAddressFormModel
import models.NewKeeperEnterAddressManuallyFormModel.NewKeeperEnterAddressManuallyCacheKey
import models.NewKeeperDetailsViewModel.{createNewKeeper, getTitle}
import models.PrivateKeeperDetailsFormModel
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.dvla.vehicles.presentation.common
import common.clientsidesession.CookieImplicits.{RichCookies, RichForm, RichResult}
import common.clientsidesession.{ClientSideSession, ClientSideSessionFactory}
import common.model.VehicleDetailsModel
import common.webserviceclients.addresslookup.AddressLookupService
import common.views.helpers.FormExtensions.formBinding
import utils.helpers.Config
import views.html.acquire.new_keeper_choose_your_address

class NewKeeperChooseYourAddress @Inject()(addressLookupService: AddressLookupService)
                                          (implicit clientSideSessionFactory: ClientSideSessionFactory,
                                           config: Config) extends Controller {

  private[controllers] val form = Form(NewKeeperChooseYourAddressFormModel.Form.Mapping)

  private final val KeeperDetailsNotInCacheMessage = "Failed to find keeper details in cache. " +
    "Now redirecting to vehicle lookup."
  private final val PrivateAndBusinessKeeperDetailsBothInCacheMessage = "Both private and business keeper details " +
    "found in cache. This is an error condition. Now redirecting to vehicle lookup."
  private final val VehicleDetailsNotInCacheMessage = "Failed to find vehicle details in cache. " +
    "Now redirecting to vehicle lookup"

  private def switch[R](onPrivate: PrivateKeeperDetailsFormModel => R,
                        onBusiness: BusinessKeeperDetailsFormModel => R,
                        onError: String => R)
                       (implicit request: Request[AnyContent]): R = {
    val privateKeeperDetailsOpt = request.cookies.getModel[PrivateKeeperDetailsFormModel]
    val businessKeeperDetailsOpt = request.cookies.getModel[BusinessKeeperDetailsFormModel]
    (privateKeeperDetailsOpt, businessKeeperDetailsOpt) match {
      case (Some(privateKeeperDetails), Some(businessKeeperDetails)) => onError(PrivateAndBusinessKeeperDetailsBothInCacheMessage)
      case (Some(privateKeeperDetails), _) => onPrivate(privateKeeperDetails)
      case (_, Some(businessKeeperDetails)) => onBusiness(businessKeeperDetails)
      case _ => onError(KeeperDetailsNotInCacheMessage)
    }
  }

  private def error(message: String): Result = {
    Logger.error(message)
    Redirect(routes.VehicleLookup.present())
  }

  def present = Action.async { implicit request =>
    switch(
      privateKeeperDetails => {
        val session = clientSideSessionFactory.getSession(request.cookies)
        fetchAddresses(privateKeeperDetails.postcode)(session, request2lang).map { addresses =>
          openView(
            constructPrivateKeeperName(privateKeeperDetails),
            privateKeeperDetails.postcode,
            privateKeeperDetails.email,
            addresses
          )
        }
      },
      businessKeeperDetails => {
        val session = clientSideSessionFactory.getSession(request.cookies)
        fetchAddresses(businessKeeperDetails.postcode)(session, request2lang).map { addresses =>
          openView(businessKeeperDetails.businessName,
            businessKeeperDetails.postcode,
            businessKeeperDetails.email,
            addresses
          )
        }
      },
      message => Future.successful(error(message))
    )
  }

  private def openView(name: String, postcode: String, email: Option[String], addresses: Seq[(String, String)])
                      (implicit request: Request[_]) = {
    request.cookies.getModel[VehicleDetailsModel] match {
      case Some(vehicleDetails) =>
        Ok(views.html.acquire.new_keeper_choose_your_address(
          NewKeeperChooseYourAddressViewModel(form.fill(), vehicleDetails),
          name,
          postcode,
          email,
          addresses
        ))
      case _ => error(VehicleDetailsNotInCacheMessage)
    }
  }

  private def handleInvalidForm(invalidForm: Form[NewKeeperChooseYourAddressFormModel],
                                name: String, postcode: String, email: Option[String], addresses: Seq[(String, String)])
                               (implicit request: Request[_]) = {
    request.cookies.getModel[VehicleDetailsModel] match {
      case Some(vehicleDetails) =>
        BadRequest(new_keeper_choose_your_address(
          NewKeeperChooseYourAddressViewModel(formWithReplacedErrors(invalidForm), vehicleDetails),
          name,
          postcode,
          email,
          addresses)
        )
      case _ => error(VehicleDetailsNotInCacheMessage)
    }
  }

  def submit = Action.async { implicit request =>
    form.bindFromRequest.fold(
      invalidForm =>
        switch(
          privateKeeperDetails => {
            implicit val session = clientSideSessionFactory.getSession(request.cookies)
            fetchAddresses(privateKeeperDetails.postcode).map { addresses =>
              handleInvalidForm(
                invalidForm,
                constructPrivateKeeperName(privateKeeperDetails),
                privateKeeperDetails.postcode,
                privateKeeperDetails.email,
                addresses
              )
            }
          },
          businessKeeperDetails => {
            implicit val session = clientSideSessionFactory.getSession(request.cookies)
            fetchAddresses(businessKeeperDetails.postcode).map { addresses =>
              handleInvalidForm(
                invalidForm,
                businessKeeperDetails.businessName,
                businessKeeperDetails.postcode,
                businessKeeperDetails.email,
                addresses
              )
            }
          },
          message => Future.successful(error(message))
        ),
      validForm =>
        switch(
          privateKeeperDetails => {
            implicit val session = clientSideSessionFactory.getSession(request.cookies)
            lookupUprn(
              validForm,
              constructPrivateKeeperName(privateKeeperDetails),
              privateKeeperDetails.email,
              None,
              isBusinessKeeper = false
            )
          },
          businessKeeperDetails => {
            implicit val session = clientSideSessionFactory.getSession(request.cookies)
            lookupUprn(
              validForm,
              businessKeeperDetails.businessName,
              businessKeeperDetails.email,
              businessKeeperDetails.fleetNumber,
              isBusinessKeeper = true
            )
          },
          message => Future.successful(error(message))
        )
    )
  }

  def back = Action { implicit request =>
    switch(
      privateKeeperDetails => Redirect(routes.PrivateKeeperDetails.present()),
      businessKeeperDetails => Redirect(routes.BusinessKeeperDetails.present()),
      message => error(message)
    )
  }

  private def constructPrivateKeeperName(privateKeeperDetails: PrivateKeeperDetailsFormModel): String =
    s"${getTitle(privateKeeperDetails.title)} ${privateKeeperDetails.firstName} ${privateKeeperDetails.lastName}"

  private def fetchAddresses(postcode: String)(implicit session: ClientSideSession, lang: Lang) =
    addressLookupService.fetchAddressesForPostcode(postcode, session.trackingId)

  private def formWithReplacedErrors(form: Form[NewKeeperChooseYourAddressFormModel])(implicit request: Request[_]) =
    form.replaceError(AddressSelectId, "error.required",
      FormError(key = AddressSelectId, message = "disposal_newKeeperChooseYourAddress.address.required", args = Seq.empty))
      .distinctErrors

  private def lookupUprn(model: NewKeeperChooseYourAddressFormModel,
                         newKeeperName: String,
                         email: Option[String],
                         fleetNumber: Option[String],
                         isBusinessKeeper: Boolean)
                        (implicit request: Request[_], session: ClientSideSession) = {
    val lookedUpAddress = addressLookupService.fetchAddressForUprn(model.uprnSelected.toString, session.trackingId)
    lookedUpAddress.map {
      case Some(addressViewModel) =>
          createNewKeeper(addressViewModel) match {
          case Some(newKeeperDetails) =>
            Redirect(routes.VehicleTaxOrSorn.present())
              .discardingCookie(NewKeeperEnterAddressManuallyCacheKey)
              .withCookie(model)
              .withCookie(newKeeperDetails)
          case _ => error("No new keeper details found in cache, redirecting to vehicle lookup")
        }
      case None => Redirect(routes.UprnNotFound.present())
    }
  }
}
