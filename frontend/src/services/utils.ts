export const updateObject = <T>(oldObject: T, updatedProperties: Partial<T>): T => {
  return {
    ...oldObject,
    ...updatedProperties,
  };
};
