package chakmed.ecommerce.products.boundary;

import chakmed.ecommerce.products.boundary.command.CreateCategoryCommand;
import chakmed.ecommerce.products.control.CategoryService;
import chakmed.ecommerce.products.entity.Category;

import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.Map;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/categories")
public class CategoriesResource {

    @Inject
    CategoryService categoryService;

    @GET
    @Produces(APPLICATION_JSON)
    public String getCategories() {

        JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder();

        Category.listAll().stream()
                .map(Category.class::cast)
                .forEach(category -> jsonObjectBuilder.add(String.valueOf(category.id), category.getLabel()));

        return jsonObjectBuilder.build().toString();
    }

    @POST
    public Response createCategory(CreateCategoryCommand createCategoryCommand) {

        var category = mapCreateCategoryCommandToCategory(createCategoryCommand);
        if(!categoryService.findByCriteria(Map.of("label", category.getLabel())).isEmpty()){
            throw new IllegalArgumentException("Category already exists");
        }
        categoryService.saveCategory(category);
        return Response.ok(category).status(201).build();
    }

    private Category mapCreateCategoryCommandToCategory(CreateCategoryCommand createCategoryCommand) {
        var category = new Category();
        category.setLabel(createCategoryCommand.getLabel());
        category.setValue(createCategoryCommand.getLabel().replace(" ", ""));

        return category;
    }

    @DELETE
    @Path("/{categoryID}")
    public Response deleteCategory(@PathParam("categoryID") String id) {

        categoryService.deleteCategory(Long.valueOf(id));
        return Response.ok().status(200).build();
    }




}