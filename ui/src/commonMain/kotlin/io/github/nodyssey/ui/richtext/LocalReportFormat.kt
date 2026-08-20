package io.github.nodyssey.ui.richtext

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.nodyssey.data.settings.ReportFormat
import io.github.plaza.designsys.richtext.RichContent

/**
 * 测评报告 = 适配格式 / 原文, as seen by whatever is drawing a post body.
 *
 * A `CompositionLocal` rather than a parameter on [RichContent] because a report can turn up in any
 * of the six places that render post markup — a thread, a signature, a message, a space readme, an
 * editor preview — and only one of them has a ViewModel that already knows about settings. Threading
 * it would mean giving the other five a settings dependency for a value none of them decides.
 *
 * The default is the app's own default, so the failure mode of a missing provider is today's
 * behaviour rather than a broken screen — unlike [RichContent]'s `voteContent`, which is a slot with
 * no sensible default and stays a parameter for exactly that reason. The provider lives in
 * `MainActivity`, beside the one that makes 站外链接 apply everywhere at once.
 */
val LocalReportFormat = staticCompositionLocalOf { ReportFormat.ADAPTED }
