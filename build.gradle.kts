plugins {
    id("dev.prism")
}

group = "com.norwood"
version = "1.0.0"

prism {
    metadata {
        modId = "ahf"
        name = "Advanced Hitbox Framework"
        description = "Adds proper hitboxes into the game"
        license = "MIT"
    }

    maven("CurseMaven", "https://www.cursemaven.com")

    version("1.20.1") {
        forge {
            loaderVersion = "47.4.18"
            loaderVersionRange = "[47,)"
            dependencies {
                modCompileOnly("curse.maven:timeless-and-classics-zero-1028108:8141310")
            }
        }
    }

    version("1.21.1") {
        neoforge {
            loaderVersion = "21.1.222"
            loaderVersionRange = "[4,)"
        }
    }

    version("26.1") {
        neoforge {
            loaderVersion = "26.1.1.0-beta"
            loaderVersionRange = "[4,)"
        }
    }

}
