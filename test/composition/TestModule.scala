package composition

import com.google.inject.name.Names
import com.tzavellas.sse.guice.ScalaModule
import composition.DevModule._
import org.scalatest.mock.MockitoSugar
import play.api.{LoggerLike, Logger}
import uk.gov.dvla.vehicles.presentation.common.filters.{DateTimeZoneServiceImpl, DateTimeZoneService}
import uk.gov.dvla.vehicles.presentation.common.services.DateService
import webserviceclients.acquire.{AcquireServiceImpl, AcquireService, AcquireWebServiceImpl, AcquireWebService}
import webserviceclients.fakes.{FakeDateServiceImpl, FakeVehicleLookupWebService, FakeAddressLookupWebServiceImpl}
import uk.gov.dvla.vehicles.presentation.common
import common.webserviceclients.vehiclelookup.{VehicleLookupServiceImpl, VehicleLookupService, VehicleLookupWebService}
import common.clientsidesession.CookieFlags
import common.clientsidesession.NoCookieFlags
import common.clientsidesession.ClientSideSessionFactory
import common.clientsidesession.ClearTextClientSideSessionFactory
import common.filters.AccessLoggingFilter.AccessLoggerName
import common.webserviceclients.addresslookup.{AddressLookupWebService, AddressLookupService}
import common.webserviceclients.bruteforceprevention.BruteForcePreventionWebService
import common.webserviceclients.bruteforceprevention.BruteForcePreventionServiceImpl
import common.webserviceclients.bruteforceprevention.BruteForcePreventionService
import webserviceclients.fakes.brute_force_protection.FakeBruteForcePreventionWebServiceImpl

class TestModule() extends ScalaModule with MockitoSugar {
  /**
   * Bind the fake implementations the traits
   */
  def configure() {
    Logger.debug("Guice is loading TestModule")
    ordnanceSurveyAddressLookup()
    bind[VehicleLookupWebService].to[FakeVehicleLookupWebService].asEagerSingleton()
    bind[VehicleLookupService].to[VehicleLookupServiceImpl].asEagerSingleton()

    bind[AcquireWebService].to[AcquireWebServiceImpl].asEagerSingleton()
    bind[AcquireService].to[AcquireServiceImpl].asEagerSingleton()

    bind[DateService].to[FakeDateServiceImpl].asEagerSingleton()
    bind[CookieFlags].to[NoCookieFlags].asEagerSingleton()
    bind[ClientSideSessionFactory].to[ClearTextClientSideSessionFactory].asEagerSingleton()

    bind[BruteForcePreventionWebService].to[FakeBruteForcePreventionWebServiceImpl].asEagerSingleton()
    bind[BruteForcePreventionService].to[BruteForcePreventionServiceImpl].asEagerSingleton()

    bind[LoggerLike].annotatedWith(Names.named(AccessLoggerName)).toInstance(Logger("dvla.pages.common.AccessLogger"))
  }

  private def ordnanceSurveyAddressLookup() = {
    bind[AddressLookupService].to[uk.gov.dvla.vehicles.presentation.common.webserviceclients.addresslookup.ordnanceservey.AddressLookupServiceImpl]

    val fakeWebServiceImpl = new FakeAddressLookupWebServiceImpl(
      responseOfPostcodeWebService = FakeAddressLookupWebServiceImpl.responseValidForPostcodeToAddress,
      responseOfUprnWebService = FakeAddressLookupWebServiceImpl.responseValidForUprnToAddress
    )
    bind[AddressLookupWebService].toInstance(fakeWebServiceImpl)
    bind[DateTimeZoneService].toInstance(new DateTimeZoneServiceImpl)
  }
}
