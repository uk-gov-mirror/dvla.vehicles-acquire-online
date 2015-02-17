package controllers

import com.google.inject.Inject
import models.AcquireCacheKeyPrefix.CookiePrefix
import play.api.mvc.{Result, Request}
import play.api.data.Form
import uk.gov.dvla.vehicles.presentation.common
import common.clientsidesession.ClientSideSessionFactory
import common.controllers.PrivateKeeperDetailsBase
import common.model.{PrivateKeeperDetailsFormModel, VehicleAndKeeperDetailsModel}
import common.services.DateService
import utils.helpers.Config

class PrivateKeeperDetails @Inject()()(implicit protected override val clientSideSessionFactory: ClientSideSessionFactory,
                                       dateService: DateService,
                                       config: Config) extends PrivateKeeperDetailsBase {

  protected override def presentResult(model: VehicleAndKeeperDetailsModel, form: Form[PrivateKeeperDetailsFormModel])(implicit request: Request[_]): Result =
    Ok(views.html.acquire.private_keeper_details(model, form))

  protected override def missingVehicleDetails(implicit request: Request[_]): Result =
    Redirect(routes.SetUpTradeDetails.present())

  protected override def invalidFormResult(model: VehicleAndKeeperDetailsModel, form: Form[PrivateKeeperDetailsFormModel])(implicit request: Request[_]): Result =
    BadRequest(views.html.acquire.private_keeper_details(model, form))

  protected override def success(implicit request: Request[_]): Result =
    Redirect(routes.NewKeeperChooseYourAddress.present())
}
