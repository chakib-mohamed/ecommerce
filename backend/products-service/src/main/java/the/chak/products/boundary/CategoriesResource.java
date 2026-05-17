package chakmed.ecommerce.products.boundary;

import chakmed.ecommerce.products.boundary.dto.SaveCategoryDto;
import chakmed.ecommerce.products.control.CategoryService;
import chakmed.ecommerce.products.entity.Category;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;

import java.util.Map;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

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
    public Response createCategory(SaveCategoryDto createCategoryCommand) {

        var category = mapCreateCategoryCommandToCategory(createCategoryCommand);
        if(!categoryService.findByCriteria(Map.of("label", category.getLabel())).isEmpty()){
            throw new IllegalArgumentException("Category already exists");
        }
        categoryService.saveCategory(category);
        return Response.ok(category).status(201).build();
    }

    private Category mapCreateCategoryCommandToCategory(SaveCategoryDto createCategoryCommand) {
        var category = new Category();
        category.setLabel(createCategoryCommand.getLabel());

        return category;
    }

    @DELETE
    @Path("/{categoryID}")
    public Response deleteCategory(@PathParam("categoryID") String id) {

        categoryService.deleteCategory(Long.valueOf(id));
        return Response.ok().status(200).build();
    }




}