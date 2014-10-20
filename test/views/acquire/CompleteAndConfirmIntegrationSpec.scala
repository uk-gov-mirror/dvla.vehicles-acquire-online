package views.acquire

import com.google.inject.{Guice, Injector}
import com.tzavellas.sse.guice.ScalaModule
import composition.{TestComposition, GlobalLike, TestModule}
import helpers.common.ProgressBar
import helpers.acquire.CookieFactoryForUISpecs
import ProgressBar.progressStep
import helpers.tags.UiTag
import helpers.UiSpec
import helpers.webbrowser.{TestGlobal, WebDriverFactory, TestHarness}
import org.openqa.selenium.{By, WebElement, WebDriver}
import org.scalatest.concurrent.Eventually
import org.scalatest.mock.MockitoSugar
import pages.common.ErrorPanel
import pages.acquire.{AcquireSuccessPage, CompleteAndConfirmPage, BeforeYouStartPage, SetupTradeDetailsPage, VehicleTaxOrSornPage}
import play.api.libs.ws.WSResponse
import play.api.test.FakeApplication
import uk.gov.dvla.vehicles.presentation.common.filters.CsrfPreventionAction
import webserviceclients.acquire.{AcquireRequestDto, AcquireServiceImpl, AcquireService, AcquireWebService}
import webserviceclients.fakes.FakeAcquireWebServiceImpl
import webserviceclients.fakes.FakeAddressLookupService.addressWithUprn
import pages.acquire.CompleteAndConfirmPage.navigate
import pages.acquire.CompleteAndConfirmPage.next
import pages.acquire.CompleteAndConfirmPage.back
import pages.acquire.CompleteAndConfirmPage.useTodaysDate
import pages.acquire.CompleteAndConfirmPage.dayDateOfSaleTextBox
import pages.acquire.CompleteAndConfirmPage.monthDateOfSaleTextBox
import pages.acquire.CompleteAndConfirmPage.mileageTextBox
import pages.acquire.CompleteAndConfirmPage.consent
import pages.acquire.CompleteAndConfirmPage.yearDateOfSaleTextBox
import webserviceclients.fakes.FakeDateServiceImpl.DateOfAcquisitionDayValid
import webserviceclients.fakes.FakeDateServiceImpl.DateOfAcquisitionMonthValid
import webserviceclients.fakes.FakeDateServiceImpl.DateOfAcquisitionYearValid
import pages.common.Feedback.AcquireEmailFeedbackLink
import uk.gov.dvla.vehicles.presentation.common.mappings.TitleType
import scala.concurrent.Future

final class CompleteAndConfirmIntegrationSpec extends UiSpec with TestHarness {

  "go to page" should {
    "display the page for a new keeper" taggedAs UiTag in new WebBrowser {
      go to BeforeYouStartPage
      cacheSetup()
      go to CompleteAndConfirmPage
      page.title should equal(CompleteAndConfirmPage.title)
    }

    "contain feedback email facility with appropriate subject" taggedAs UiTag in new WebBrowser {
      go to BeforeYouStartPage
      cacheSetup()
      go to CompleteAndConfirmPage

      page.source.contains(AcquireEmailFeedbackLink) should equal(true)
    }

    "display the progress of the page when progressBar is set to true" taggedAs UiTag in new ProgressBarTrue {
      go to BeforeYouStartPage
      cacheSetup()
      go to CompleteAndConfirmPage
      page.source.contains(progressStep(8)) should equal(true)
    }

    "not display the progress of the page when progressBar is set to false" taggedAs UiTag in new ProgressBarFalse {
      go to BeforeYouStartPage
      cacheSetup()
      go to CompleteAndConfirmPage
      page.source.contains(progressStep(8)) should equal(false)
    }

    "Redirect when no new keeper details are cached" taggedAs UiTag in new WebBrowser {
      go to CompleteAndConfirmPage
      page.title should equal(SetupTradeDetailsPage.title)
    }

    "contain the hidden csrfToken field" taggedAs UiTag in new WebBrowser {
      go to CompleteAndConfirmPage
      val csrf: WebElement = webDriver.findElement(By.name(CsrfPreventionAction.TokenName))
      csrf.getAttribute("type") should equal("hidden")
      csrf.getAttribute("name") should equal(uk.gov.dvla.vehicles.presentation.common.filters.CsrfPreventionAction.TokenName)
      csrf.getAttribute("value").size > 0 should equal(true)
    }
  }

  "submit button" should {
    "go to the appropriate next page when all details are entered for a new keeper" taggedAs UiTag in new WebBrowser {
      go to BeforeYouStartPage
      cacheSetup()
      navigate()
      page.title should equal(AcquireSuccessPage.title)
    }

    "go to the appropriate next page when mandatory details are entered for a new keeper" taggedAs UiTag in new WebBrowser {
      go to BeforeYouStartPage
      cacheSetup()
      navigate(mileage = "")
      page.title should equal(AcquireSuccessPage.title)
    }

    val countingWebService = new FakeAcquireWebServiceImpl {
      var calls = List[(AcquireRequestDto, String)]()

      override def callAcquireService(request: AcquireRequestDto, trackingId: String): Future[WSResponse] = {
        calls ++= List(request -> trackingId)
        super.callAcquireService(request, trackingId)
      }
    }

    object MockAcquireServiceCompositionGlobal extends GlobalLike with TestComposition {
      override lazy val injector: Injector = TestGlobal.testInjector(new ScalaModule with MockitoSugar {
        override def configure() {
          bind[AcquireWebService].toInstance(countingWebService)
        }
      })
    }

    "be disabled after click" taggedAs UiTag in new WebBrowser(
      app = FakeApplication(withGlobal = Some(MockAcquireServiceCompositionGlobal)),
      webDriver = WebDriverFactory.webDriver(javascriptEnabled = true)
    ) {
      go to BeforeYouStartPage
      cacheSetup()
      go to CompleteAndConfirmPage

      mileageTextBox enter CompleteAndConfirmPage.MileageValid
      dayDateOfSaleTextBox enter CompleteAndConfirmPage.DayDateOfSaleValid
      monthDateOfSaleTextBox enter CompleteAndConfirmPage.MonthDateOfSaleValid
      yearDateOfSaleTextBox enter CompleteAndConfirmPage.YearDateOfSaleValid
      click on consent

      next.underlying.getAttribute("class") should not include "disabled"
      CompleteAndConfirmPage.singleClickSubmit
      CompleteAndConfirmPage.singleClickSubmit
      CompleteAndConfirmPage.singleClickSubmit
      CompleteAndConfirmPage.singleClickSubmit
      CompleteAndConfirmPage.singleClickSubmit
      Eventually.eventually(next.underlying.getAttribute("class").contains("disabled"))
      Eventually.eventually(page.title == AcquireSuccessPage.title)

//      Thread.sleep(500)
//      countingWebService.calls should have size 1
    }

    "display one validation error message when a mileage is entered greater than max length for a new keeper" taggedAs UiTag in new WebBrowser {
      go to BeforeYouStartPage
      cacheSetup()
      navigate(mileage = "1000000")
      ErrorPanel.numberOfErrors should equal(1)
    }

    "display one validation error message when a mileage is entered less than min length for a new keeper" taggedAs UiTag in new WebBrowser {
      go to BeforeYouStartPage
      cacheSetup()
      navigate(mileage = "-1")
      ErrorPanel.numberOfErrors should equal(1)
    }

    "display one validation error message when a mileage containing letters is entered for a new keeper" taggedAs UiTag in new WebBrowser {
      go to BeforeYouStartPage
      cacheSetup()
      navigate(mileage = "a")
      ErrorPanel.numberOfErrors should equal(1)
    }

    "display one validation error message when day date of sale is empty for a new keeper" taggedAs UiTag in new WebBrowser {
      go to BeforeYouStartPage
      cacheSetup()
      navigate(dayDateOfSale = "")
      ErrorPanel.numberOfErrors should equal(1)
    }

    "display one validation error message when month date of sale is empty for a new keeper" taggedAs UiTag in new WebBrowser {
      go to BeforeYouStartPage
      cacheSetup()
      navigate(monthDateOfSale = "")
      ErrorPanel.numberOfErrors should equal(1)
    }

    "display one validation error message when year date of sale is empty for a new keeper" taggedAs UiTag in new WebBrowser {
      go to BeforeYouStartPage
      cacheSetup()
      navigate(yearDateOfSale = "")
      ErrorPanel.numberOfErrors should equal(1)
    }

    "display one validation error message when day date of sale contains letters for a new keeper" taggedAs UiTag in new WebBrowser {
      go to BeforeYouStartPage
      cacheSetup()
      navigate(dayDateOfSale = "a")
      ErrorPanel.numberOfErrors should equal(1)
    }

    "display one validation error message when month date of sale contains letters for a new keeper" taggedAs UiTag in new WebBrowser {
      go to BeforeYouStartPage
      cacheSetup()
      navigate(monthDateOfSale = "a")
      ErrorPanel.numberOfErrors should equal(1)
    }

    "display one validation error message when year date of sale contains letters for a new keeper" taggedAs UiTag in new WebBrowser {
      go to BeforeYouStartPage
      cacheSetup()
      navigate(yearDateOfSale = "a")
      ErrorPanel.numberOfErrors should equal(1)
    }
  }

  "use todays date" should {
    "input todays date into date of sale for a new keeper" taggedAs UiTag in new WebBrowserWithJs {
      go to BeforeYouStartPage
      cacheSetup()
      go to CompleteAndConfirmPage

      click on useTodaysDate

      dayDateOfSaleTextBox.value should equal (DateOfAcquisitionDayValid)
      monthDateOfSaleTextBox.value should equal (DateOfAcquisitionMonthValid)
      yearDateOfSaleTextBox.value should equal (DateOfAcquisitionYearValid)
    }
  }

  "back" should {
    "display previous page when back link is clicked for a new keeper" taggedAs UiTag in new WebBrowser {
      go to BeforeYouStartPage
      CookieFactoryForUISpecs.
        setupTradeDetails().
        dealerDetails(addressWithUprn).
        vehicleDetails().
        privateKeeperDetails().
        newKeeperDetails(
          title = Some(TitleType(1,"")),
          address = addressWithUprn
        ).vehicleTaxOrSornFormModel()

      go to CompleteAndConfirmPage
      click on back
      page.title should equal(VehicleTaxOrSornPage.title)
    }
  }

  private def cacheSetup()(implicit webDriver: WebDriver) =
    CookieFactoryForUISpecs.
      setupTradeDetails()
      .dealerDetails()
      .vehicleDetails()
      .newKeeperDetails()
      .vehicleLookupFormModel()
      .vehicleTaxOrSornFormModel()
}
