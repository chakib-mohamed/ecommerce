import React, { useState, useEffect } from "react";
import AddCategory from "../../components/AddCategory/AddCategory";
import Categories from "../../components/Categories/Categories";
import { service } from "../../services";

export const ManageCategories = () => {
  const [categories, setCategories] = useState(null);

  useEffect(() => {
    fetchCategories();
  }, []);

  const reloadCategories = () => {
    fetchCategories();
  };

  const fetchCategories = () => {
    service.fetchCategories().then((categories) => {
      categories = categories.map((cat) => ({
        id: cat.value,
        label: cat.name,
      }));
      setCategories(categories);
    });
  };

  return (
    <React.Fragment>
      <h4 className="container w-50">Add a new category :</h4>
      <AddCategory onCategoryAdded={reloadCategories} />

      <h4 className="container w-50 mt-4">List of categories:</h4>
      {categories && (
        <Categories
          categories={categories}
          onCategoryDeleted={reloadCategories}
        />
      )}
    </React.Fragment>
  );
};

export default ManageCategories;
