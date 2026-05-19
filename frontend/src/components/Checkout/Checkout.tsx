import { useForm } from "react-hook-form";
import { OrderCommand } from "../../types/types";

type Props = {
  onCheckout: (orderCommand: OrderCommand) => void;
};

const Checkout = ({ onCheckout }: Props) => {
  const {
    register,
    handleSubmit,
    formState: { errors, isValid, isSubmitting },
  } = useForm<OrderCommand>({
    mode: "onBlur",
  });

  const onSubmitForm = (orderCommand: OrderCommand) => {
    onCheckout(orderCommand);
  };

  return (
    <div className="space-y-8">
      <div className="text-center sm:text-left space-y-2">
        <h2 className="text-2xl font-black text-slate-900 tracking-tight">Payment Details</h2>
        <p className="text-slate-500 font-medium text-sm italic">Complete your purchase securely</p>
      </div>

      <form onSubmit={handleSubmit(onSubmitForm)} className="space-y-6">
        {/* Card Number */}
        <div className="space-y-1">
          <label htmlFor="cardNumber" className="block text-sm font-bold text-slate-700 ml-1">
            Card Number
          </label>
          <div className="relative group">
            <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-slate-400 group-focus-within:text-blue-500 transition-colors">
              <i className="fa fa-credit-card"></i>
            </div>
            <input
              id="cardNumber"
              placeholder="0000 0000 0000 0000"
              className={`block w-full pl-11 pr-4 py-4 bg-slate-50 border-2 rounded-2xl text-slate-900 placeholder-slate-400 transition-all duration-300 outline-none
                ${errors.cardNumber ? 'border-red-100 focus:border-red-500 bg-red-50/30' : 'border-transparent focus:border-blue-500 focus:bg-white shadow-sm focus:shadow-blue-100'}`}
              {...register("cardNumber", {
                required: "Card number is required",
                minLength: { value: 16, message: "Must be 16 digits" },
                maxLength: { value: 16, message: "Must be 16 digits" },
                pattern: { value: /^[0-9]+$/, message: "Only digits allowed" },
              })}
            />
          </div>
          {errors.cardNumber && (
            <p className="mt-1 text-xs font-bold text-red-500 ml-1 italic animate-in fade-in slide-in-from-left-1">
              {errors.cardNumber.message}
            </p>
          )}
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
          {/* Expiration Date */}
          <div className="space-y-1">
            <label htmlFor="expirationDate" className="block text-sm font-bold text-slate-700 ml-1">
              Expiry Date
            </label>
            <div className="relative group">
              <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-slate-400 group-focus-within:text-blue-500 transition-colors">
                <i className="fa fa-calendar"></i>
              </div>
              <input
                id="expirationDate"
                placeholder="MM/YY"
                className={`block w-full pl-11 pr-4 py-4 bg-slate-50 border-2 rounded-2xl text-slate-900 placeholder-slate-400 transition-all duration-300 outline-none
                  ${errors.expirationDate ? 'border-red-100 focus:border-red-500 bg-red-50/30' : 'border-transparent focus:border-blue-500 focus:bg-white shadow-sm focus:shadow-blue-100'}`}
                {...register("expirationDate", {
                  required: "Required",
                  pattern: {
                    value: /^(0[1-9]|1[0-2])\/?([0-9]{4}|[0-9]{2})$/,
                    message: "Use MM/YY",
                  },
                })}
              />
            </div>
            {errors.expirationDate && (
              <p className="mt-1 text-xs font-bold text-red-500 ml-1 italic animate-in fade-in slide-in-from-left-1">
                {errors.expirationDate.message}
              </p>
            )}
          </div>

          {/* Validation Number */}
          <div className="space-y-1">
            <label htmlFor="validationNumber" className="block text-sm font-bold text-slate-700 ml-1">
              CVC / CVV
            </label>
            <div className="relative group">
              <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-slate-400 group-focus-within:text-blue-500 transition-colors">
                <i className="fa fa-lock"></i>
              </div>
              <input
                id="validationNumber"
                placeholder="123"
                className={`block w-full pl-11 pr-4 py-4 bg-slate-50 border-2 rounded-2xl text-slate-900 placeholder-slate-400 transition-all duration-300 outline-none
                  ${errors.validationNumber ? 'border-red-100 focus:border-red-500 bg-red-50/30' : 'border-transparent focus:border-blue-500 focus:bg-white shadow-sm focus:shadow-blue-100'}`}
                {...register("validationNumber", {
                  required: "Required",
                  minLength: { value: 3, message: "3 digits" },
                  maxLength: { value: 3, message: "3 digits" },
                  pattern: { value: /^[0-9]+$/, message: "Digits only" },
                })}
              />
            </div>
            {errors.validationNumber && (
              <p className="mt-1 text-xs font-bold text-red-500 ml-1 italic animate-in fade-in slide-in-from-left-1">
                {errors.validationNumber.message}
              </p>
            )}
          </div>
        </div>

        <div className="pt-4">
          <button
            type="submit"
            disabled={!isValid || isSubmitting}
            className={`w-full py-5 rounded-2xl text-white font-bold text-lg shadow-lg transform transition-all duration-300 flex items-center justify-center space-x-3
              ${!isValid || isSubmitting 
                ? 'bg-slate-300 cursor-not-allowed border-0' 
                : 'bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700 hover:shadow-blue-500/25 active:scale-[0.98] border-0 cursor-pointer shadow-blue-500/20'}`}
          >
            {isSubmitting ? (
              <div className="w-6 h-6 border-3 border-white/30 border-t-white rounded-full animate-spin"></div>
            ) : (
              <>
                <i className="fa fa-shield"></i>
                <span>Complete Purchase</span>
              </>
            )}
          </button>
        </div>
      </form>
      
      <div className="flex flex-col items-center justify-center space-y-4 pt-4">
        <div className="flex items-center space-x-6 grayscale opacity-40">
          <i className="fa fa-cc-visa text-2xl"></i>
          <i className="fa fa-cc-mastercard text-2xl"></i>
          <i className="fa fa-cc-paypal text-2xl"></i>
          <i className="fa fa-cc-amex text-2xl"></i>
        </div>
        <p className="text-[10px] text-slate-400 uppercase tracking-widest font-bold flex items-center">
          <i className="fa fa-lock mr-2 text-blue-500"></i>
          Secure 256-bit SSL Encrypted Payment
        </p>
      </div>
    </div>
  );
};

export default Checkout;
