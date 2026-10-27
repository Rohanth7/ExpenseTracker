package com.example.expensetracker.ui.screens.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

class SetupGateTest {
    @Test fun `finished setup goes to the app`() =
        assertEquals(SetupGate.Decision.App, SetupGate.decide(completed = true, firstLaunchChecked = true, hasData = true))

    @Test fun `a new install gets setup`() =
        assertEquals(SetupGate.Decision.Setup, SetupGate.decide(completed = false, firstLaunchChecked = false, hasData = false))

    @Test fun `an install from before setup existed skips it once`() =
        assertEquals(SetupGate.Decision.SkipForUpgrade, SetupGate.decide(completed = false, firstLaunchChecked = false, hasData = true))

    // A restore or an SMS captured mid-setup adds data; setup still isn't finished.
    @Test fun `data added during setup doesn't end it`() =
        assertEquals(SetupGate.Decision.Setup, SetupGate.decide(completed = false, firstLaunchChecked = true, hasData = true))
}
