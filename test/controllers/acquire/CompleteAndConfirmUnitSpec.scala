package controllers.acquire

import controllers.acquire.Common.PrototypeHtml
import controllers.{PrivateKeeperDetails, CompleteAndConfirm}
import helpers.UnitSpec
import helpers.acquire.CookieFactoryForUnitSpecs
import helpers.acquire.CookieFactoryForUnitSpecs.KeeperEmail
import models.CompleteAndConfirmFormModel.Form.{MileageId, DateOfSaleId, ConsentId}
import org.mockito.Mockito.when
import pages.acquire.AcquireSuccessPage
import pages.acquire.CompleteAndConfirmPage.{MileageValid, ConsentTrue}
import pages.acquire.CompleteAndConfirmPage.{DayDateOfSaleValid, MonthDateOfSaleValid, YearDateOfSaleValid}
import pages.acquire.VehicleLookupPage
import play.api.test.Helpers.{LOCATION, BAD_REQUEST, OK, contentAsString, defaultAwaitTimeout}
import play.api.test.{FakeRequest, WithApplication}
import uk.gov.dvla.vehicles.presentation.common.clientsidesession.ClientSideSessionFactory
import uk.gov.dvla.vehicles.presentation.common.mappings.DayMonthYear.{DayId, MonthId, YearId}
import utils.helpers.Config

class CompleteAndConfirmUnitSpec extends UnitSpec {

  "present" should {
    "display the page with new keeper cached" in new WithApplication {
      whenReady(present) { r =>
        r.header.status should equal(OK)
      }
    }

    "display prototype message when config set to true" in new WithApplication {
      contentAsString(present) should include(PrototypeHtml)
    }

    "not display prototype message when config set to false" in new WithApplication {
      val request = FakeRequest()
      implicit val clientSideSessionFactory = injector.getInstance(classOf[ClientSideSessionFactory])
      implicit val config: Config = mock[Config]
      when(config.isPrototypeBannerVisible).thenReturn(false)

      val privateKeeperDetailsPrototypeNotVisible = new PrivateKeeperDetails()
      val result = privateKeeperDetailsPrototypeNotVisible.present(request)
      contentAsString(result) should not include PrototypeHtml
    }

    "present a full form when new keeper and vehicle details cookies are present for new keeper" in new WithApplication {
      val request = FakeRequest().
        withCookies(CookieFactoryForUnitSpecs.newPrivateKeeperDetailsModel()).
        withCookies(CookieFactoryForUnitSpecs.vehicleDetailsModel()).
        withCookies(CookieFactoryForUnitSpecs.completeAndConfirmModel())
      val content = contentAsString(completeAndConfirm.present(request))
      content should include(s"""value="$MileageValid"""")
      content should include("""value="true"""") // Checkbox value
      content should include(s"""value="$YearDateOfSaleValid"""")
    }

    "display empty fields when new keeper complete cookie does not exist" in new WithApplication {
      val request = FakeRequest()
      val result = completeAndConfirm.present(request)
      val content = contentAsString(result)
      content should not include MileageValid
    }

    "redirect to vehicle lookup when no new keeper details cookie is present" in new WithApplication {
      val request = FakeRequest()
      val result = completeAndConfirm.present(request)
      whenReady(result) { r =>
        r.header.headers.get(LOCATION) should equal(Some(VehicleLookupPage.address))
      }
    }

//    "play back business keeper details as expected" in new WithApplication() {
//      val fleetNumber = "12345-"
//      val request = FakeRequest().
//        withCookies(CookieFactoryForUnitSpecs.newPrivateKeeperDetailsModel(isBusinessKeeper = true, fleetNumber = Some(fleetNumber))).
//        withCookies(CookieFactoryForUnitSpecs.vehicleDetailsModel())
//      val content = contentAsString(completeAndConfirm.present(request))
//      content should include("<dt>Fleet number</dt>")
//      content should include(s"<dd>$fleetNumber</dd>")
//      content should include(s"<dd>$KeeperEmail</dd>")
//    }

    "play back private keeper details as expected" in new WithApplication() {
      val request = FakeRequest().
        withCookies(CookieFactoryForUnitSpecs.newPrivateKeeperDetailsModel(isBusinessKeeper = false)).
        withCookies(CookieFactoryForUnitSpecs.vehicleDetailsModel())
      val content = contentAsString(completeAndConfirm.present(request))
      content should not include "<dt>Fleet number</dt>"
      content should include(s"<dd>$KeeperEmail</dd>")
    }
  }

  "submit" should {
    "replace numeric mileage error message for with standard error message" in new WithApplication {
      val request = buildCorrectlyPopulatedRequest(mileage = "$$").
        withCookies(CookieFactoryForUnitSpecs.newPrivateKeeperDetailsModel()).
        withCookies(CookieFactoryForUnitSpecs.vehicleDetailsModel())

      val result = completeAndConfirm.submit(request)
      val replacementMileageErrorMessage = "You must enter a valid mileage between 0 and 999999"
      replacementMileageErrorMessage.r.findAllIn(contentAsString(result)).length should equal(2)
    }

    "redirect to next page when mandatory fields are complete for new keeper" in new WithApplication {
      val request = buildCorrectlyPopulatedRequest().
        withCookies(CookieFactoryForUnitSpecs.newPrivateKeeperDetailsModel())

      val result = completeAndConfirm.submit(request)
      whenReady(result) { r =>
        r.header.headers.get(LOCATION) should equal (Some(AcquireSuccessPage.address)) //ToDo - update when next section is implemented
      }
    }

    "redirect to next page when all fields are complete for new keeper" in new WithApplication {
      val request = buildCorrectlyPopulatedRequest().
        withCookies(CookieFactoryForUnitSpecs.newPrivateKeeperDetailsModel())

      val result = completeAndConfirm.submit(request)
      whenReady(result) { r =>
        r.header.headers.get(LOCATION) should equal (Some(AcquireSuccessPage.address)) //ToDo - update when next section is implemented
      }
    }

    "return a bad request if consent is not ticked" in new WithApplication {
      val request = buildCorrectlyPopulatedRequest(consent="").
        withCookies(CookieFactoryForUnitSpecs.newPrivateKeeperDetailsModel()).
        withCookies(CookieFactoryForUnitSpecs.vehicleDetailsModel())

      val result = completeAndConfirm.submit(request)
      whenReady(result) { r =>
        r.header.status should equal(BAD_REQUEST)
      }
    }
  }

  private def buildCorrectlyPopulatedRequest(mileage: String = MileageValid,
                                             dayDateOfSale: String = DayDateOfSaleValid,
                                             monthDateOfSale: String = MonthDateOfSaleValid,
                                             yearDateOfSale: String = YearDateOfSaleValid,
                                             consent: String = ConsentTrue) = {
    FakeRequest().withFormUrlEncodedBody(
      MileageId -> mileage,
      s"$DateOfSaleId.$DayId" -> dayDateOfSale,
      s"$DateOfSaleId.$MonthId" -> monthDateOfSale,
      s"$DateOfSaleId.$YearId" -> yearDateOfSale,
      ConsentId -> consent
    )
  }

  private val completeAndConfirm = {
    injector.getInstance(classOf[CompleteAndConfirm])
  }

  private lazy val present = {
    val request = FakeRequest().
      withCookies(CookieFactoryForUnitSpecs.newPrivateKeeperDetailsModel()).
      withCookies(CookieFactoryForUnitSpecs.vehicleDetailsModel())
    completeAndConfirm.present(request)
  }
}