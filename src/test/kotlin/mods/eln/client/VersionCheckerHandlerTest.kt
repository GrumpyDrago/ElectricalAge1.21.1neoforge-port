package mods.eln.client

import mods.eln.client.VersionCheckerHandler.Result
import org.semver4j.Semver
import kotlin.test.Test
import kotlin.test.assertEquals

class VersionCheckerHandlerTest {
    private fun release(tag: String, prerelease: Boolean = false, draft: Boolean = false) =
        """{"tag_name": "$tag", "prerelease": $prerelease, "draft": $draft, "html_url": "https://example.com/$tag"}"""

    private fun check(current: String, vararg releases: String) =
        VersionCheckerHandler.evaluate(releases.joinToString(",", "[", "]"), Semver.parse(current)!!)

    @Test
    fun newestReleaseWinsWhateverTheOrder() {
        assertEquals<Result>(
            Result.NewVersion("2.10.0", "https://example.com/v2.10.0"),
            check("2.0.0", release("v2.9.0"), release("v2.10.0"), release("2.1.0"))
        )
    }

    @Test
    fun sameAndOlderReleases() {
        assertEquals<Result>(Result.UpToDate, check("2.0.0", release("2.0.0"), release("1.9.0")))
        assertEquals<Result>(Result.UnderTest, check("2.1.0", release("2.0.0")))
    }

    @Test
    fun preReleasesAreForThoseWhoUseThem() {
        assertEquals<Result>(
            Result.UpToDate,
            check("2.0.0", release("2.0.0"), release("2.1.0-beta.1"), release("2.2.0", prerelease = true))
        )
        assertEquals<Result>(
            Result.NewVersion("2.0.0-alpha.2", "https://example.com/v2.0.0-alpha.2"),
            check("2.0.0-alpha.1", release("v2.0.0-alpha.2", prerelease = true))
        )
        assertEquals<Result>(
            Result.NewVersion("2.0.0", "https://example.com/2.0.0"),
            check("2.0.0-alpha.1", release("2.0.0"))
        )
    }

    @Test
    fun draftsAndOtherTagsDoNotCount() {
        assertEquals<Result>(Result.NothingReleased, check("2.0.0"))
        assertEquals<Result>(Result.NothingReleased, check("2.0.0", release("nightly"), release("3.0.0", draft = true)))
    }
}
