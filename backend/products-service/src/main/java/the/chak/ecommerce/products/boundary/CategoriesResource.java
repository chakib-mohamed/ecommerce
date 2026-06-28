package the.chak.ecommerce.products.boundary;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import the.chak.ecommerce.products.boundary.dto.CategoryDto;
import the.chak.ecommerce.products.boundary.dto.Criteria;
import the.chak.ecommerce.products.boundary.dto.SaveCategoryDto;
import the.chak.ecommerce.products.boundary.mapper.CategoryMapper;
import the.chak.ecommerce.products.control.CategoryService;

@Path("/categories")
public class CategoriesResource {

    @Inject
    CategoryService categoryService;

    @Inject
    CategoryMapper categoryMapper;

    @GET
    @Produces(APPLICATION_JSON)
    public List<CategoryDto> getCategories(@QueryParam("page") @DefaultValue("0") int pageIndex,
            @QueryParam("size") @DefaultValue("20") int pageSize) {

        return categoryService.getRootCategories(pageIndex, pageSize).stream()
                .map(categoryMapper::toDto).collect(Collectors.toList());
    }

    @POST
    public Response createCategory(@Valid SaveCategoryDto createCategoryCommand) {

        var category = categoryMapper.toEntity(createCategoryCommand);
        categoryService.saveCategory(category);
        return Response.status(Response.Status.CREATED).entity(categoryMapper.toDto(category)).build();
    }

    @PUT
    public Response updateCategory(@Valid CategoryDto categoryDto) {
        var category = categoryMapper.toEntity(categoryDto);
        categoryService.updateCategory(category);
        return Response.ok(categoryMapper.toDto(category)).status(200).build();
    }

    @POST
    @Path("/search")
    @Produces(APPLICATION_JSON)
    public List<CategoryDto> searchCategories(Map<String, Criteria> searchRequest,
            @QueryParam("page") @DefaultValue("0") int pageIndex,
            @QueryParam("size") @DefaultValue("20") int pageSize) {

        return categoryService.findByCriteria(searchRequest, pageIndex, pageSize).stream()
                .map(categoryMapper::toDto).collect(Collectors.toList());
    }

    @DELETE
    @Path("/{categoryID}")
    public Response deleteCategory(@PathParam("categoryID") String id) {

        categoryService.deleteCategory(Long.valueOf(id));
        return Response.ok().status(200).build();
    }

}
