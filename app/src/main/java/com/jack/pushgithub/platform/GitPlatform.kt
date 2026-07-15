package com.jack.pushgithub.platform

/**
 * Git 平台抽象，支持 GitHub / GitLab / Gitee
 */
sealed class GitPlatform {
    abstract val name: String
    abstract val apiBase: String
    abstract val defaultBranch: String

    data object GitHub : GitPlatform() {
        override val name = "GitHub"
        override val apiBase = "https://api.github.com"
        override val defaultBranch = "main"
    }

    data object GitLab : GitPlatform() {
        override val name = "GitLab"
        override val apiBase = "https://gitlab.com/api/v4"
        override val defaultBranch = "main"
    }

    data object Gitee : GitPlatform() {
        override val name = "Gitee"
        override val apiBase = "https://gitee.com/api/v5"
        override val defaultBranch = "master"
    }

    companion object {
        fun detect(url: String): GitPlatform = when {
            url.contains("github.com") -> GitHub
            url.contains("gitlab.com") -> GitLab
            url.contains("gitee.com") -> Gitee
            else -> GitHub
        }

        fun buildCloneUrl(platform: GitPlatform, url: String): String {
            val clean = url.trimEnd('/')
            return when (platform) {
                GitHub -> {
                    if (clean.startsWith("https://github.com/")) {
                        if (clean.endsWith(".git")) clean else "$clean.git"
                    } else {
                        "https://github.com/${clean.removeSuffix(".git")}.git"
                    }
                }
                GitLab -> {
                    if (clean.startsWith("https://gitlab.com/")) {
                        if (clean.endsWith(".git")) clean else "$clean.git"
                    } else {
                        "https://gitlab.com/${clean.removeSuffix(".git")}.git"
                    }
                }
                Gitee -> {
                    if (clean.startsWith("https://gitee.com/")) {
                        if (clean.endsWith(".git")) clean else "$clean.git"
                    } else {
                        "https://gitee.com/${clean.removeSuffix(".git")}.git"
                    }
                }
            }
        }
    }
}