package controllers.acquire

import controllers.VehicleLookup
import helpers.acquire.CookieFactoryForUnitSpecs
import helpers.common.CookieHelper.{fetchCookiesFromHeaders, verifyCookieHasBeenDiscarded}
import helpers.UnitSpec
import helpers.WithApplication
import models.VehicleLookupFormModel.Form.{DocumentReferenceNumberId, VehicleRegistrationNumberId, VehicleSoldToId}
import models.{BusinessKeeperDetailsCacheKeys, PrivateKeeperDetailsCacheKeys}
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import pages.acquire.{EnterAddressManuallyPage, BusinessChooseYourAddressPage, BusinessKeeperDetailsPage, PrivateKeeperDetailsPage, SetupTradeDetailsPage, MicroServiceErrorPage}
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.WSResponse
import play.api.test.FakeRequest
import play.api.test.Helpers.{LOCATION, contentAsString, defaultAwaitTimeout}
import uk.gov.dvla.vehicles.presentation.common
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import common.clientsidesession.ClientSideSessionFactory
import common.mappings.DocumentReferenceNumber
import common.services.DateServiceImpl
import common.webserviceclients.vehicleandkeeperlookup.VehicleAndKeeperDetailsRequest
import common.webserviceclients.vehicleandkeeperlookup.VehicleAndKeeperDetailsResponse
import common.webserviceclients.vehicleandkeeperlookup.VehicleAndKeeperLookupServiceImpl
import common.webserviceclients.vehicleandkeeperlookup.VehicleAndKeeperLookupWebService
import common.webserviceclients.bruteforceprevention.BruteForcePreventionConfig
import common.webserviceclients.bruteforceprevention.BruteForcePreventionServiceImpl
import common.webserviceclients.bruteforceprevention.BruteForcePreventionWebService
import common.webserviceclients.bruteforceprevention.BruteForcePreventionService
import utils.helpers.Config
import views.acquire.VehicleLookup.{VehicleSoldTo_Private, VehicleSoldTo_Business}
import webserviceclients.fakes.FakeAddressLookupService.BuildingNameOrNumberValid
import webserviceclients.fakes.FakeAddressLookupService.Line2Valid
import webserviceclients.fakes.FakeAddressLookupService.Line3Valid
import webserviceclients.fakes.FakeAddressLookupService.PostTownValid
import webserviceclients.fakes.FakeAddressLookupService.TraderBusinessNameValid
import webserviceclients.fakes.FakeAddressLookupWebServiceImpl.UprnValid
import webserviceclients.fakes.{FakeDateServiceImpl, FakeResponse}
import webserviceclients.fakes.FakeVehicleAndKeeperLookupWebService.ReferenceNumberValid
import webserviceclients.fakes.FakeVehicleAndKeeperLookupWebService.RegistrationNumberValid
import webserviceclients.fakes.FakeVehicleAndKeeperLookupWebService.vehicleDetailsResponseSuccess
import webserviceclients.fakes.brute_force_protection.FakeBruteForcePreventionWebServiceImpl
import webserviceclients.fakes.brute_force_protection.FakeBruteForcePreventionWebServiceImpl.responseFirstAttempt
import webserviceclients.fakes.brute_force_protection.FakeBruteForcePreventionWebServiceImpl.responseSecondAttempt
import webserviceclients.fakes.brute_force_protection.FakeBruteForcePreventionWebServiceImpl.VrmThrows
import pages.acquire.BusinessKeeperDetailsPage.EmailValid

final class VehicleLookupUnitSpec extends UnitSpec {

  "present" should {
    "display the page" in new WithApplication {
      present.futureValue.header.status should equal(play.api.http.Status.OK)
    }

    "redirect to setupTradeDetails page when user has not set up a trader for disposal" in new WithApplication {
      val request = buildCorrectlyPopulatedRequest()
      val result = vehicleLookupResponseGenerator().present(request)

      result.futureValue.header.headers.get(LOCATION) should equal(Some(SetupTradeDetailsPage.address))
    }

    "display populated fields when cookie exists" in new WithApplication {
      val request = FakeRequest().
        withCookies(CookieFactoryForUnitSpecs.traderDetailsModel()).
        withCookies(CookieFactoryForUnitSpecs.vehicleLookupFormModel())
      val result = vehicleLookupResponseGenerator().present(request)
      val content = contentAsString(result)
      content should include(ReferenceNumberValid)
      content should include(RegistrationNumberValid)
    }

    "display data captured in previous pages" in new WithApplication {
      val request = FakeRequest().
        withCookies(CookieFactoryForUnitSpecs.traderDetailsModel())
      val result = vehicleLookupResponseGenerator().present(request)
      val content = contentAsString(result)

      content should include(TraderBusinessNameValid)
      content should include(BuildingNameOrNumberValid)
      content should include(Line2Valid)
      content should include(Line3Valid)
      content should include(PostTownValid)
      content should include(webserviceclients.fakes.FakeAddressLookupService.PostcodeValid)
    }

    "display trader email captured in previous pages" in new WithApplication {
      val request = FakeRequest().
        withCookies(CookieFactoryForUnitSpecs.traderDetailsModel())
      val result = vehicleLookupResponseGenerator().present(request)
      val content = contentAsString(result)

      content should include(EmailValid)
    }

    "display empty fields when cookie does not exist" in new WithApplication {
      val request = FakeRequest().
        withCookies(CookieFactoryForUnitSpecs.vehicleAndKeeperDetailsModel())
      val result = vehicleLookupResponseGenerator().present(request)
      val content = contentAsString(result)
      content should not include ReferenceNumberValid
      content should not include RegistrationNumberValid
    }
  }

  "submit" should {
    "replace max length error message for document reference number with standard error message" in new WithApplication {
      val request = buildCorrectlyPopulatedRequest(referenceNumber = "1" * (DocumentReferenceNumber.MaxLength + 1)).
        withCookies(CookieFactoryForUnitSpecs.traderDetailsModel())
      val result = vehicleLookupResponseGenerator().submit(request)
      // check the validation summary text
      "Document reference number - Document reference number must be an 11-digit number".
        r.findAllIn(contentAsString(result)).length should equal(1)
      // check the form item validation
      "\"error\">Document reference number must be an 11-digit number".
        r.findAllIn(contentAsString(result)).length should equal(1)
    }

    "replace required and min length error messages for document reference number with standard error message" in new WithApplication {
      val request = buildCorrectlyPopulatedRequest(referenceNumber = "").
        withCookies(CookieFactoryForUnitSpecs.traderDetailsModel())
      val result = vehicleLookupResponseGenerator().submit(request)
      // check the validation summary text
      "Document reference number - Document reference number must be an 11-digit number".
        r.findAllIn(contentAsString(result)).length should equal(1)
      // check the form item validation
      "\"error\">Document reference number must be an 11-digit number".
        r.findAllIn(contentAsString(result)).length should equal(1)
    }

    "replace max length error message for vehicle registration number with standard error message (US43)" in new WithApplication {
      val request = buildCorrectlyPopulatedRequest(registrationNumber = "PJ05YYYX").
        withCookies(CookieFactoryForUnitSpecs.traderDetailsModel())
      val result = vehicleLookupResponseGenerator().submit(request)
      val count = "Must be as shown on the latest V5C".r.findAllIn(contentAsString(result)).length

      count should equal(2)
    }

    "replace required and min length error messages for vehicle registration number with standard error message" in new WithApplication {
      val request = buildCorrectlyPopulatedRequest(registrationNumber = "").
        withCookies(CookieFactoryForUnitSpecs.traderDetailsModel())
      val result = vehicleLookupResponseGenerator().submit(request)
      val count = "Must be as shown on the latest V5C".r.findAllIn(contentAsString(result)).length

      count should equal(2) // The same message is displayed in 2 places - once in the validation-summary at the top of the page and once above the field.
    }

    "redirect to BusinessChooseYourAddress when back button is pressed and there is no manual address cookie in cache" in new WithApplication {
      val request = FakeRequest()
      val result = vehicleLookupResponseGenerator().back(request)

      result.futureValue.header.headers.get(LOCATION) should equal(Some(BusinessChooseYourAddressPage.address))
    }

    "redirect to EnterAddressManually when back button is pressed and the user has manually entered an address" in new WithApplication {
      val request = buildCorrectlyPopulatedRequest().
        withCookies(CookieFactoryForUnitSpecs.enterAddressManually())
      val result = vehicleLookupResponseGenerator().back(request)

      result.futureValue.header.headers.get(LOCATION) should equal(Some(EnterAddressManuallyPage.address))
    }

    "redirect to PrivateKeeperDetails when submit button clicked and Private Individual is selected" in new WithApplication {
      val request = buildCorrectlyPopulatedRequest(ReferenceNumberValid, RegistrationNumberValid, VehicleSoldTo_Private).
        withCookies(CookieFactoryForUnitSpecs.traderDetailsModel(uprn = Some(UprnValid)))
      val result = vehicleLookupResponseGenerator().submit(request)

      result.futureValue.header.headers.get(LOCATION) should equal(Some(PrivateKeeperDetailsPage.address))
    }

    "redirect to BusinessKeeperDetails when submit button clicked and Business is selected" in new WithApplication {
      val request = buildCorrectlyPopulatedRequest(ReferenceNumberValid, RegistrationNumberValid, VehicleSoldTo_Business).
        withCookies(CookieFactoryForUnitSpecs.traderDetailsModel(uprn = Some(UprnValid)))
      val result = vehicleLookupResponseGenerator().submit(request)

      result.futureValue.header.headers.get(LOCATION) should equal(Some(BusinessKeeperDetailsPage.address))
    }

    "redirect to MicroserviceError when microservice throws" in new WithApplication {
      val request = buildCorrectlyPopulatedRequest()
      val result = vehicleLookupError.submit(request)

      whenReady(result) { r =>
        r.header.headers.get(LOCATION) should equal(Some(MicroServiceErrorPage.address))
      }
    }

    "remove all business keeper cookies when private keeper is selected" in new WithApplication {
      val request = buildCorrectlyPopulatedRequest().
        withCookies(CookieFactoryForUnitSpecs.traderDetailsModel(uprn = Some(UprnValid))).
        withCookies(CookieFactoryForUnitSpecs.businessKeeperDetailsModel()).
        withCookies(CookieFactoryForUnitSpecs.newKeeperChooseYourAddress())

      val result = vehicleLookupResponseGenerator().submit(request)
      whenReady(result) { r =>
        val cookies = fetchCookiesFromHeaders(r)
        BusinessKeeperDetailsCacheKeys.foreach(verifyCookieHasBeenDiscarded(_, cookies))
      }
    }

    "remove all private keeper cookies when business keeper is selected" in new WithApplication {
      val request = buildCorrectlyPopulatedRequest(soldTo = VehicleSoldTo_Business).
        withCookies(CookieFactoryForUnitSpecs.traderDetailsModel(uprn = Some(UprnValid))).
        withCookies(CookieFactoryForUnitSpecs.privateKeeperDetailsModel()).
        withCookies(CookieFactoryForUnitSpecs.newKeeperChooseYourAddress())

      val result = vehicleLookupResponseGenerator().submit(request)
      whenReady(result) { r =>
        val cookies = fetchCookiesFromHeaders(r)
        PrivateKeeperDetailsCacheKeys.foreach(verifyCookieHasBeenDiscarded(_, cookies))
      }
    }
  }

  private def responseThrows: Future[WSResponse] = Future {
    throw new RuntimeException("This error is generated deliberately by a test")
  }

  private def bruteForceServiceImpl(permitted: Boolean): BruteForcePreventionService = {

    def bruteForcePreventionWebService: BruteForcePreventionWebService = {
      val status = if (permitted) play.api.http.Status.OK else play.api.http.Status.FORBIDDEN
      val bruteForcePreventionWebService: BruteForcePreventionWebService = mock[BruteForcePreventionWebService]

      when(bruteForcePreventionWebService.callBruteForce(RegistrationNumberValid)).
        thenReturn(Future.successful(new FakeResponse(status = status, fakeJson = responseFirstAttempt)))

      when(bruteForcePreventionWebService.callBruteForce(FakeBruteForcePreventionWebServiceImpl.VrmAttempt2)).
        thenReturn(Future.successful(new FakeResponse(status = status, fakeJson = responseSecondAttempt)))

      when(bruteForcePreventionWebService.callBruteForce(FakeBruteForcePreventionWebServiceImpl.VrmLocked)).
        thenReturn(Future.successful(new FakeResponse(status = status)))

      when(bruteForcePreventionWebService.callBruteForce(VrmThrows)).thenReturn(responseThrows)

      when(bruteForcePreventionWebService.reset(any[String])).
        thenReturn(Future.successful(new FakeResponse(status = play.api.http.Status.OK)))

      bruteForcePreventionWebService
    }

    new BruteForcePreventionServiceImpl(
      config = new BruteForcePreventionConfig,
      ws = bruteForcePreventionWebService,
      dateService = new FakeDateServiceImpl
    )
  }

  private def vehicleLookupResponseGenerator(fullResponse: (Int, Option[VehicleAndKeeperDetailsResponse]) = vehicleDetailsResponseSuccess,
                                             bruteForceService: BruteForcePreventionService = bruteForceServiceImpl(permitted = true)) = {
    val (status, vehicleDetailsResponse) = fullResponse
    val ws: VehicleAndKeeperLookupWebService = mock[VehicleAndKeeperLookupWebService]
    when(ws.invoke(any[VehicleAndKeeperDetailsRequest], any[String])).thenReturn(Future {
      val responseAsJson: Option[JsValue] = vehicleDetailsResponse match {
        case Some(e) => Some(Json.toJson(e))
        case _ => None
      }
      new FakeResponse(status = status, fakeJson = responseAsJson) // Any call to a webservice will always return this successful response.
    })
    val vehicleAndKeeperLookupServiceImpl = new VehicleAndKeeperLookupServiceImpl(ws)
    implicit val clientSideSessionFactory = injector.getInstance(classOf[ClientSideSessionFactory])
    implicit val config: Config = mock[Config]

    new VehicleLookup(
      bruteForceService = bruteForceService,
      vehicleAndKeeperLookupService = vehicleAndKeeperLookupServiceImpl,
      dateService = dateService
    )
  }

  private lazy val vehicleLookupError = {
    val permitted = true // The lookup is permitted as we want to test failure on the vehicle lookup micro-service step.
    val vehicleAndKeeperLookupWebService: VehicleAndKeeperLookupWebService = mock[VehicleAndKeeperLookupWebService]
    when(vehicleAndKeeperLookupWebService.invoke(any[VehicleAndKeeperDetailsRequest], any[String])).thenReturn(Future {
      throw new IllegalArgumentException
    })
    val vehicleAndKeeperLookupServiceImpl = new VehicleAndKeeperLookupServiceImpl(vehicleAndKeeperLookupWebService)
    implicit val clientSideSessionFactory = injector.getInstance(classOf[ClientSideSessionFactory])
    implicit val config: Config = mock[Config]

    new VehicleLookup(
      bruteForceService = bruteForceServiceImpl(permitted = permitted),
      vehicleAndKeeperLookupService = vehicleAndKeeperLookupServiceImpl,
      dateService = dateService
    )
  }

  private def buildCorrectlyPopulatedRequest(referenceNumber: String = ReferenceNumberValid,
                                             registrationNumber: String = RegistrationNumberValid,
                                             soldTo: String = VehicleSoldTo_Private
                                              ) = {
    FakeRequest().withFormUrlEncodedBody(
      DocumentReferenceNumberId -> referenceNumber,
      VehicleRegistrationNumberId -> registrationNumber,
      VehicleSoldToId -> soldTo)
  }

  private lazy val present = {
    val request = FakeRequest().
      withCookies(CookieFactoryForUnitSpecs.traderDetailsModel())
    vehicleLookupResponseGenerator(vehicleDetailsResponseSuccess).present(request)
  }

  private val dateService = new DateServiceImpl
}