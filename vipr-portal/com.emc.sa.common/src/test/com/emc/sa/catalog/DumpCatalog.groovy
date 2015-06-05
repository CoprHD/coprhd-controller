import com.google.gson.Gson
import com.emc.sa.catalog.CategoryDef
import com.emc.sa.catalog.ServiceDef

def showServices = {String level, ServiceDef service ->
    println "$level$service.title (description: $service.description, image: $service.image)"
}

def showCategories = {}
showCategories = {String level, CategoryDef category ->
    println "$level$category.title (description: $category.description, image: $category.image)"
    category.categories.each(showCategories.curry("$level  "))
    category.services.each(showServices.curry("$level- "))
}

new Gson().fromJson(new FileReader("portal/sawebapp/conf/default-catalog.json"),
    CategoryDef.class).categories.each(showCategories.curry(""))
