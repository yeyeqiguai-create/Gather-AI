package com.tim.futureai.domain.chat

/** A reusable prompt kept locally for quick insertion into the chat draft. */
data class PromptTemplate(
    val id: String,
    val title: String,
    val prompt: String,
    val category: String,
)

object BuiltInPromptLibrary {
    val categories = listOf("全部", "写作", "学习", "工作", "编程", "生活")

    val prompts = listOf(
        PromptTemplate("write-structure", "梳理文章结构", "请围绕【主题】设计一份清晰的文章大纲，列出核心观点、论据和段落顺序。", "写作"),
        PromptTemplate("write-polish", "润色一段文字", "请在保留原意和语气的前提下，润色下面这段文字，并简要说明主要修改：\n\n【粘贴文字】", "写作"),
        PromptTemplate("write-short", "压缩重点", "把下面内容压缩成易读的要点，保留关键事实、数字和行动项：\n\n【粘贴内容】", "写作"),
        PromptTemplate("learn-simple", "讲清一个概念", "请用直白的语言解释【概念】，先给一句话定义，再用一个日常例子说明，最后列出常见误解。", "学习"),
        PromptTemplate("learn-quiz", "生成自测题", "根据【主题或材料】设计 5 道由浅入深的自测题。先只给题目，等我作答后再逐题讲解。", "学习"),
        PromptTemplate("learn-compare", "比较两个方案", "请比较【方案 A】和【方案 B】，按适用场景、优势、限制、成本与主要风险整理，并给出有条件的建议。", "学习"),
        PromptTemplate("work-meeting", "整理会议记录", "请把下面记录整理为：结论、待办事项（负责人/期限若已提及）、未解决问题。不要补造缺失信息。\n\n【粘贴记录】", "工作"),
        PromptTemplate("work-plan", "拆解执行计划", "把【目标】拆成可执行步骤，标出依赖、预计产出和需要我确认的事项。信息不足时先列出假设。", "工作"),
        PromptTemplate("work-email", "起草工作邮件", "请为【收件人/场景】起草一封【语气】的邮件，目标是【目标】。控制篇幅，并保留需要我补充的字段。", "工作"),
        PromptTemplate("code-review", "审查代码", "请审查下面代码，优先指出正确性、安全性和边界条件问题。每项附上影响与最小修复建议；没有证据的问题不要猜测。\n\n【粘贴代码】", "编程"),
        PromptTemplate("code-explain", "解释代码逻辑", "请按执行顺序解释下面代码，指出输入、输出、关键状态变化和可能失败的边界情况。\n\n【粘贴代码】", "编程"),
        PromptTemplate("life-options", "规划几种选择", "我想完成【目标】，目前条件是【时间/预算/限制】。请给出 3 种可行方案，说明取舍，并列出开始前需要确认的问题。", "生活"),
    )
}
